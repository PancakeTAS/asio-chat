package gay.pancake.asiochat.audio;

import com.synthbot.jasiohost.AsioChannel;
import com.synthbot.jasiohost.AsioDriver;
import com.synthbot.jasiohost.AsioDriverListener;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * ASIO audio device.
 *
 * @author Pancake
 */
public class AudioDevice implements AsioDriverListener {

    /** The ASIO driver */
    private final AsioDriver driver;

    /** The input channel */
    private AsioChannel inputChannel;
    /** The output channels */
    private AsioChannel leftOutputChannel, rightOutputChannel;

    /** The buffer */
    private byte[] buffer;

    /** The callback for when samples are received */
    private final Consumer<byte[]> callback;
    /** The sample rate and buffer size callback */
    private Consumer<Integer> sampleRateCallback, bufferSizeCallback;

    /**
     * Create a new audio device
     *
     * @param asio The name of the ASIO driver to use
     * @param callback The callback for when samples are received
     */
    public AudioDevice(String asio, Consumer<byte[]> callback) {
        this.driver = AsioDriver.getDriver(asio);
        this.driver.addAsioDriverListener(this);
        this.callback = callback;
    }

    /**
     * Start the ASIO driver and trigger the callbacks
     *
     * @param inputChannel The input channel to use
     * @param leftOutputChannel The left output channel to use
     * @param rightOutputChannel The right output channel to use
     */
    public void start(int inputChannel, int leftOutputChannel, int rightOutputChannel) {
        this.inputChannel = this.driver.getChannelInput(inputChannel);
        this.leftOutputChannel = this.driver.getChannelOutput(leftOutputChannel);
        this.rightOutputChannel = this.driver.getChannelOutput(rightOutputChannel);

        var set = new HashSet<AsioChannel>();
        set.add(this.inputChannel);
        set.add(this.leftOutputChannel);
        set.add(this.rightOutputChannel);

        this.driver.createBuffers(set);

        if (this.bufferSizeCallback != null)
            this.bufferSizeCallback.accept(this.driver.getBufferPreferredSize());

        if (this.sampleRateCallback != null)
            this.sampleRateCallback.accept((int) this.driver.getSampleRate());

        this.buffer = new byte[this.driver.getBufferPreferredSize() * 4];
        this.driver.start();
    }

    @Override
    public void bufferSwitch(long sampleTime, long samplePosition, Set<AsioChannel> activeChannels) {
        // read input channel
        this.inputChannel.getByteBuffer().get(this.buffer);
        this.callback.accept(this.buffer);

        // write output channels
        this.leftOutputChannel.getByteBuffer().put(this.buffer);
        this.rightOutputChannel.getByteBuffer().put(this.buffer);
    }

    /**
     * Stop the ASIO driver
     */
    public void stop() {
        this.driver.stop();
        this.driver.disposeBuffers();
        this.driver.exit();
        this.driver.shutdownAndUnloadDriver();
    }

    @Override
    public void sampleRateDidChange(double sampleRate) {
        if (this.sampleRateCallback != null)
            this.sampleRateCallback.accept((int) sampleRate);
    }

    @Override
    public void bufferSizeChanged(int bufferSize) {
        this.buffer = new byte[this.driver.getBufferPreferredSize() * 4];
        if (this.bufferSizeCallback != null)
            this.bufferSizeCallback.accept(bufferSize);
    }

    /**
     * Set a callback for when the sample rate changes
     *
     * @param callback The callback
     */
    public void onSampleRateChange(Consumer<Integer> callback) {
        this.sampleRateCallback = callback;
    }

    /**
     * Set a callback for when the buffer size changes
     *
     * @param callback The callback
     */
    public void onBufferSizeChange(Consumer<Integer> callback) {
        this.bufferSizeCallback = callback;
    }

    /**
     * Get the sample rate
     *
     * @return The sample rate
     */
    public int getSampleRate() {
        return (int) this.driver.getSampleRate();
    }

    /**
     * Get the buffer size
     *
     * @return The buffer size
     */
    public int getBufferSize() {
        return this.driver.getBufferPreferredSize();
    }

    @Override public void resetRequest() {}
    @Override public void resyncRequest() {}
    @Override public void latenciesChanged(int inputLatency, int outputLatency) {}

}
