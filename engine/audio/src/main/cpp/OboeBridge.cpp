#include <jni.h>
#include <oboe/Oboe.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstdint>
#include <memory>
#include <mutex>
#include <vector>

namespace {

constexpr int32_t kRingFrames = 1 << 16;
constexpr int32_t kTapFrames = 1 << 13;
constexpr int32_t kDelayMax = 48000;

class Ring {
public:
    void reset() {
        writePos.store(0, std::memory_order_relaxed);
        readPos.store(0, std::memory_order_relaxed);
    }

    int32_t write(const int16_t *src, int32_t count) {
        int32_t w = writePos.load(std::memory_order_relaxed);
        int32_t r = readPos.load(std::memory_order_acquire);
        int32_t free = kRingFrames - (w - r);
        int32_t n = std::min(count, free);
        for (int32_t i = 0; i < n; ++i) {
            data[(w + i) & (kRingFrames - 1)] = src[i];
        }
        writePos.store(w + n, std::memory_order_release);
        return n;
    }

    int32_t available() const {
        return writePos.load(std::memory_order_acquire) - readPos.load(std::memory_order_relaxed);
    }

    int32_t read(int16_t *dest, int32_t max) {
        int32_t r = readPos.load(std::memory_order_relaxed);
        int32_t w = writePos.load(std::memory_order_acquire);
        int32_t n = std::min(max, w - r);
        for (int32_t i = 0; i < n; ++i) {
            dest[i] = data[(r + i) & (kRingFrames - 1)];
        }
        readPos.store(r + n, std::memory_order_release);
        return n;
    }

private:
    int16_t data[kRingFrames]{};
    std::atomic<int32_t> writePos{0};
    std::atomic<int32_t> readPos{0};
};

class DropRing {
public:
    void reset() {
        writePos.store(0);
        readPos.store(0);
    }

    void write(const int16_t *src, int32_t count) {
        int32_t w = writePos.load(std::memory_order_relaxed);
        for (int32_t i = 0; i < count; ++i) {
            data[(w + i) & (kTapFrames - 1)] = src[i];
        }
        writePos.store(w + count, std::memory_order_release);
        int32_t r = readPos.load(std::memory_order_relaxed);
        if ((w + count) - r > kTapFrames - 64) {
            readPos.store(w + count - (kTapFrames / 2), std::memory_order_release);
        }
    }

    int16_t readOrZero() {
        int32_t r = readPos.load(std::memory_order_relaxed);
        int32_t w = writePos.load(std::memory_order_acquire);
        if (r >= w) return 0;
        int16_t s = data[r & (kTapFrames - 1)];
        readPos.store(r + 1, std::memory_order_release);
        return s;
    }

private:
    int16_t data[kTapFrames]{};
    std::atomic<int32_t> writePos{0};
    std::atomic<int32_t> readPos{0};
};

struct MixerState {
    std::vector<int16_t> backingHold;
    std::atomic<const int16_t *> backingPtr{nullptr};
    std::atomic<int32_t> backingLen{0};
    std::atomic<int32_t> playHead{0};
    std::atomic<bool> loop{true};
    std::atomic<float> reverb{0.f};
    std::atomic<float> delayAmt{0.f};
    std::atomic<float> voiceGain{1.f};
    std::atomic<float> trackGain{0.72f};
    std::atomic<float> gate{0.02f};
    std::atomic<float> eqLow{1.f};
    std::atomic<float> eqMid{1.f};
    std::atomic<float> eqHigh{1.f};
    std::atomic<float> compress{0.f};
    std::atomic<bool> paused{false};
    float delayLine[kDelayMax]{};
    int32_t delayIndex{0};
    float rev{0.f};
    float prevX{0.f};
    float prevY{0.f};
    float lp{0.f};
    float env{0.f};

    void render(void *audioData, int32_t numFrames, oboe::AudioFormat format, DropRing &tap);
};

class Engine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    Ring dry;
    DropRing tap;
    MixerState mix;
    std::mutex mu;
    std::condition_variable cv;
    std::atomic<bool> running{false};
    std::shared_ptr<oboe::AudioStream> input;
    std::shared_ptr<oboe::AudioStream> output;

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *stream,
        void *audioData,
        int32_t numFrames
    ) override {
        if (!running.load(std::memory_order_relaxed)) {
            return oboe::DataCallbackResult::Continue;
        }
        if (stream == input.get()) {
            copyInput(audioData, numFrames, stream->getFormat());
            cv.notify_all();
            return oboe::DataCallbackResult::Continue;
        }
        mix.render(audioData, numFrames, stream->getFormat(), tap);
        return oboe::DataCallbackResult::Continue;
    }

    void onErrorAfterClose(oboe::AudioStream *, oboe::Result) override {
        running.store(false);
        cv.notify_all();
    }

private:
    void copyInput(void *audioData, int32_t numFrames, oboe::AudioFormat format) {
        if (format == oboe::AudioFormat::I16) {
            auto *src = static_cast<const int16_t *>(audioData);
            dry.write(src, numFrames);
            tap.write(src, numFrames);
            return;
        }
        if (format != oboe::AudioFormat::Float) return;
        auto *src = static_cast<const float *>(audioData);
        int16_t tmp[1024];
        int32_t left = numFrames;
        int32_t off = 0;
        while (left > 0) {
            int32_t n = std::min(left, 1024);
            for (int32_t i = 0; i < n; ++i) {
                float x = std::clamp(src[off + i], -1.f, 1.f);
                tmp[i] = static_cast<int16_t>(x * 32767.f);
            }
            dry.write(tmp, n);
            tap.write(tmp, n);
            left -= n;
            off += n;
        }
    }

};

void MixerState::render(void *audioData, int32_t numFrames, oboe::AudioFormat format, DropRing &tap) {
    const float hp = 0.986f;
    const float delayMix = delayAmt.load(std::memory_order_relaxed) * 0.35f;
    const float reverbMix = reverb.load(std::memory_order_relaxed) * 0.28f;
    const float vg = voiceGain.load(std::memory_order_relaxed);
    const float tg = trackGain.load(std::memory_order_relaxed);
    const float gateAmt = gate.load(std::memory_order_relaxed);
    const float lowG = eqLow.load(std::memory_order_relaxed);
    const float midG = eqMid.load(std::memory_order_relaxed);
    const float highG = eqHigh.load(std::memory_order_relaxed);
    const float comp = compress.load(std::memory_order_relaxed);
    int32_t head = playHead.load(std::memory_order_relaxed);
    const int16_t *bedPtr = backingPtr.load(std::memory_order_acquire);
    const int32_t bedLen = backingLen.load(std::memory_order_acquire);
    const bool doLoop = loop.load(std::memory_order_relaxed);
    if (paused.load(std::memory_order_relaxed)) {
        if (format == oboe::AudioFormat::Float) {
            std::fill(static_cast<float *>(audioData), static_cast<float *>(audioData) + numFrames, 0.f);
        } else {
            std::fill(static_cast<int16_t *>(audioData), static_cast<int16_t *>(audioData) + numFrames, 0);
        }
        return;
    }
    for (int32_t i = 0; i < numFrames; ++i) {
        float incoming = tap.readOrZero() / 32768.f;
        float dryS = hp * (prevY + incoming - prevX);
        prevX = incoming;
        prevY = dryS;
        env = env * 0.996f + std::fabs(dryS) * 0.004f;
        if (env < gateAmt) {
            dryS *= env / std::max(gateAmt, 1e-5f);
        }
        lp = lp * 0.94f + dryS * 0.06f;
        const float high = dryS - lp;
        dryS = lp * lowG + dryS * midG + high * highG;
        if (comp > 0.f && env > 0.18f) {
            const float reduced = 0.18f / env;
            dryS *= 1.f - comp + comp * reduced;
        }
        float delayed = delayLine[delayIndex];
        delayLine[delayIndex] = dryS + delayed * 0.25f;
        delayIndex = (delayIndex + 1) % kDelayMax;
        rev = rev * 0.92f + dryS * 0.08f;
        float wet = std::tanh(dryS + delayed * delayMix + rev * reverbMix);
        float bed = 0.f;
        if (bedPtr != nullptr && bedLen > 0) {
            if (head >= bedLen) {
                head = doLoop ? 0 : bedLen - 1;
            }
            bed = bedPtr[head] / 32768.f;
            if (head < bedLen - 1 || doLoop) head++;
        }
        float out = std::clamp(wet * vg + bed * tg, -1.f, 1.f);
        if (format == oboe::AudioFormat::Float) {
            static_cast<float *>(audioData)[i] = out;
        } else {
            static_cast<int16_t *>(audioData)[i] = static_cast<int16_t>(out * 22000.f);
        }
    }
    playHead.store(head, std::memory_order_relaxed);
}

Engine gEngine;

bool openOne(
    std::shared_ptr<oboe::AudioStream> &dest,
    oboe::Direction direction,
    int32_t sampleRate,
    bool voicePerformance
) {
    dest.reset();
    oboe::AudioStreamBuilder builder;
    builder.setDirection(direction)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::I16)
        ->setChannelCount(1)
        ->setSampleRate(sampleRate)
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        ->setDataCallback(&gEngine)
        ->setErrorCallback(&gEngine);
    if (direction == oboe::Direction::Input) {
        builder.setInputPreset(
            voicePerformance ? oboe::InputPreset::VoicePerformance : oboe::InputPreset::Unprocessed
        );
    } else {
        builder.setUsage(oboe::Usage::Media);
        builder.setContentType(oboe::ContentType::Music);
    }
    auto result = builder.openStream(dest);
    if (result != oboe::Result::OK) {
        builder.setSharingMode(oboe::SharingMode::Shared);
        result = builder.openStream(dest);
    }
    if (result != oboe::Result::OK || !dest) {
        dest.reset();
        return false;
    }
    if (dest->requestStart() != oboe::Result::OK) {
        dest->close();
        dest.reset();
        return false;
    }
    return true;
}

void closeAll() {
    gEngine.running.store(false);
    gEngine.cv.notify_all();
    if (gEngine.input) {
        gEngine.input->stop();
        gEngine.input->close();
        gEngine.input.reset();
    }
    if (gEngine.output) {
        gEngine.output->stop();
        gEngine.output->close();
        gEngine.output.reset();
    }
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_swaralab_engine_audio_OboeMic_nativeStart(
    JNIEnv *,
    jclass,
    jint sampleRate,
    jboolean voicePerformance,
    jboolean monitor
) {
    closeAll();
    gEngine.dry.reset();
    gEngine.tap.reset();
    gEngine.mix.playHead.store(0);
    gEngine.mix.paused.store(false);
    gEngine.mix.rev = 0.f;
    gEngine.mix.prevX = 0.f;
    gEngine.mix.prevY = 0.f;
    gEngine.mix.lp = 0.f;
    gEngine.mix.env = 0.f;
    gEngine.mix.delayIndex = 0;
    std::fill(gEngine.mix.delayLine, gEngine.mix.delayLine + kDelayMax, 0.f);
    gEngine.running.store(true);
    if (!openOne(gEngine.input, oboe::Direction::Input, sampleRate, voicePerformance == JNI_TRUE)) {
        gEngine.running.store(false);
        return 0;
    }
    if (monitor == JNI_TRUE && openOne(gEngine.output, oboe::Direction::Output, sampleRate, false)) {
        return 2;
    }
    return 1;
}

extern "C" JNIEXPORT void JNICALL
Java_com_swaralab_engine_audio_OboeMic_nativeSetBacking(
    JNIEnv *env,
    jclass,
    jshortArray samples,
    jboolean loop,
    jint startFrame
) {
    if (samples == nullptr) {
        gEngine.mix.backingPtr.store(nullptr, std::memory_order_release);
        gEngine.mix.backingLen.store(0, std::memory_order_release);
        gEngine.mix.backingHold.clear();
        gEngine.mix.playHead.store(0);
        return;
    }
    jsize n = env->GetArrayLength(samples);
    std::vector<int16_t> copy(n);
    env->GetShortArrayRegion(samples, 0, n, copy.data());
    gEngine.mix.backingHold.swap(copy);
    gEngine.mix.backingPtr.store(gEngine.mix.backingHold.data(), std::memory_order_release);
    gEngine.mix.backingLen.store(n, std::memory_order_release);
    gEngine.mix.loop.store(loop == JNI_TRUE);
    int32_t start = std::clamp(startFrame, 0, std::max(0, n - 1));
    gEngine.mix.playHead.store(start);
}

extern "C" JNIEXPORT void JNICALL
Java_com_swaralab_engine_audio_OboeMic_nativeSetMonitor(
    JNIEnv *,
    jclass,
    jfloat reverb,
    jfloat delay,
    jfloat voiceGain,
    jfloat trackGain
) {
    gEngine.mix.reverb.store(std::clamp(reverb, 0.f, 1.f));
    gEngine.mix.delayAmt.store(std::clamp(delay, 0.f, 1.f));
    gEngine.mix.voiceGain.store(std::clamp(voiceGain, 0.f, 2.f));
    gEngine.mix.trackGain.store(std::clamp(trackGain, 0.f, 2.f));
}

extern "C" JNIEXPORT void JNICALL
Java_com_swaralab_engine_audio_OboeMic_nativeSetPaused(
    JNIEnv *,
    jclass,
    jboolean paused
) {
    gEngine.mix.paused.store(paused == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_swaralab_engine_audio_OboeMic_nativeSeek(
    JNIEnv *,
    jclass,
    jint startFrame
) {
    int32_t len = gEngine.mix.backingLen.load(std::memory_order_acquire);
    int32_t start = std::clamp(startFrame, 0, std::max(0, len - 1));
    gEngine.mix.playHead.store(start, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_swaralab_engine_audio_OboeMic_nativeSetEffect(
    JNIEnv *env,
    jclass,
    jint type,
    jfloatArray params
) {
    float p[4] = {0.f, 0.f, 0.f, 0.f};
    if (params != nullptr) {
        jsize n = std::min(env->GetArrayLength(params), 4);
        env->GetFloatArrayRegion(params, 0, n, p);
    }
    switch (type) {
        case 2:
            gEngine.mix.reverb.store(std::clamp(p[0], 0.f, 1.f));
            gEngine.mix.delayAmt.store(std::clamp(p[1], 0.f, 1.f));
            break;
        case 3:
            gEngine.mix.eqLow.store(std::clamp(p[0], 0.25f, 4.f));
            gEngine.mix.eqMid.store(std::clamp(p[1], 0.25f, 4.f));
            gEngine.mix.eqHigh.store(std::clamp(p[2], 0.25f, 4.f));
            break;
        case 4:
            gEngine.mix.gate.store(std::clamp(p[0], 0.f, 0.2f));
            break;
        case 5:
            gEngine.mix.compress.store(std::clamp(p[0], 0.f, 1.f));
            break;
        default:
            gEngine.mix.reverb.store(0.f);
            gEngine.mix.delayAmt.store(0.f);
            gEngine.mix.gate.store(0.02f);
            gEngine.mix.eqLow.store(1.f);
            gEngine.mix.eqMid.store(1.f);
            gEngine.mix.eqHigh.store(1.f);
            gEngine.mix.compress.store(0.f);
            break;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_swaralab_engine_audio_OboeMic_nativeRead(
    JNIEnv *env,
    jclass,
    jshortArray dest,
    jint length
) {
    if (dest == nullptr || length <= 0) return 0;
    jsize cap = env->GetArrayLength(dest);
    int32_t want = std::min(length, static_cast<int32_t>(cap));
    jshort *ptr = env->GetShortArrayElements(dest, nullptr);
    if (ptr == nullptr) return 0;
    {
        std::unique_lock<std::mutex> lock(gEngine.mu);
        gEngine.cv.wait_for(lock, std::chrono::milliseconds(40), [] {
            return !gEngine.running.load() || gEngine.dry.available() > 0;
        });
    }
    int32_t got = gEngine.dry.read(reinterpret_cast<int16_t *>(ptr), want);
    if (got == 0 && gEngine.running.load()) {
        std::unique_lock<std::mutex> lock(gEngine.mu);
        gEngine.cv.wait_for(lock, std::chrono::milliseconds(20));
        got = gEngine.dry.read(reinterpret_cast<int16_t *>(ptr), want);
    }
    env->ReleaseShortArrayElements(dest, ptr, 0);
    return got;
}

extern "C" JNIEXPORT void JNICALL
Java_com_swaralab_engine_audio_OboeMic_nativeStop(JNIEnv *, jclass) {
    closeAll();
    gEngine.mix.backingPtr.store(nullptr, std::memory_order_release);
    gEngine.mix.backingLen.store(0, std::memory_order_release);
    gEngine.mix.backingHold.clear();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_swaralab_engine_audio_OboeMic_nativeIsRunning(JNIEnv *, jclass) {
    return gEngine.running.load() && gEngine.input ? JNI_TRUE : JNI_FALSE;
}
