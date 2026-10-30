package com.pos.ui;

import java.awt.Toolkit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

final class Beeper {
    private static final Logger LOG = Logger.getLogger(Beeper.class.getName());
    private static final float SAMPLE_RATE = 44100.0F;
    private static final double FREQUENCY = 1760.0;
    private static final int DURATION_MS = 70;
    private static final byte[] TONE = makeTone();
    private static final ExecutorService PLAYER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pos-beep");
        thread.setDaemon(true);
        return thread;
    });

    private Beeper() {
    }

    static void beep() {
        PLAYER.execute(Beeper::play);
    }

    private static void play() {
        AudioFormat format = new AudioFormat(44100.0F, 16, 1, true, false);

        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format);
            line.start();
            line.write(TONE, 0, TONE.length);
            line.drain();
        } catch (Exception var6) {
            LOG.log(Level.FINE, "Sound line unavailable, using the system beep", (Throwable)var6);
            Toolkit.getDefaultToolkit().beep();
        }
    }

    private static byte[] makeTone() {
        int samples = 3087;
        int fade = samples / 10;
        byte[] data = new byte[samples * 2];

        for (int i = 0; i < samples; i++) {
            double envelope = Math.min(1.0, (double)Math.min(i, samples - 1 - i) / fade);
            short value = (short)(Math.sin(11058.406140636072 * i / 44100.0) * envelope * 0.35 * 32767.0);
            data[2 * i] = (byte)value;
            data[2 * i + 1] = (byte)(value >> 8);
        }

        return data;
    }
}
