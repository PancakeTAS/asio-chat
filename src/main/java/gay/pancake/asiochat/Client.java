package gay.pancake.asiochat;

import com.synthbot.jasiohost.AsioChannel;
import com.synthbot.jasiohost.AsioDriver;
import com.synthbot.jasiohost.AsioDriverListener;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * asio-chat client
 *
 * @author Pancake
 */
public class Client implements AsioDriverListener {

    /** The ASIO driver */
    private final AsioDriver driver;

    /** The input channel */
    private AsioChannel inputChannel;
    /** The output channels */
    private AsioChannel leftOutputChannel, rightOutputChannel;

    /** The socket */
    private Socket socket;
    /** The input stream */
    private DataInputStream in;
    /** The output stream */
    private DataOutputStream out;

    /** The sample rate and buffer size */
    private int sampleRate, bufferSize;
    /** The buffer */
    private byte[] buffer;

    /** The exit callback */
    private final CompletableFuture<Void> exitCallback = new CompletableFuture<>();

    /**
     * Create a new client
     *
     * @param asio The name of the ASIO driver to use
     */
    public Client(String asio) {
        this.driver = AsioDriver.getDriver(asio);
        this.driver.addAsioDriverListener(this);
    }

    /**
     * Connect to the server
     *
     * @throws IOException If the connection fails
     */
    public void connect(String host, int port) throws IOException {
        this.socket = new Socket();
        this.socket.connect(new InetSocketAddress(host, port), 10000);

        this.in = new DataInputStream(new BufferedInputStream(this.socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(this.socket.getOutputStream()));

        var bufferSize = this.in.readInt();
        if (bufferSize != this.driver.getBufferPreferredSize())
            throw new IOException("Buffer size mismatch: " + bufferSize + " != " + this.driver.getBufferPreferredSize());

        var sampleRate = this.in.readInt();
        if (sampleRate != this.driver.getSampleRate())
            throw new IOException("Sample rate mismatch: " + sampleRate + " != " + (int) this.driver.getSampleRate());

        this.sampleRate = sampleRate;
        this.bufferSize = bufferSize;
        this.buffer = new byte[bufferSize * 4];
    }

    /**
     * Start the ASIO driver
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
        this.driver.start();
    }

    @Override
    public void bufferSwitch(long sampleTime, long samplePosition, Set<AsioChannel> activeChannels) {
        try {
            if (this.socket.isClosed())
                return;

            // read input channel
            this.inputChannel.getByteBuffer().get(this.buffer);
            this.out.write(this.buffer, 0, this.buffer.length);

            if (this.in.available() < this.buffer.length)
                return;

            // write output channels
            var read = this.in.read(this.buffer, 0, this.buffer.length);
            this.leftOutputChannel.getByteBuffer().put(this.buffer, 0, read);
            this.rightOutputChannel.getByteBuffer().put(this.buffer, 0, read);
        } catch (SocketException e) {
            Arrays.fill(this.buffer, (byte) 0);
            this.leftOutputChannel.getByteBuffer().put(this.buffer);
            this.rightOutputChannel.getByteBuffer().put(this.buffer);
            this.exitCallback.completeExceptionally(e);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Return the exit callback
     *
     * @return The exit callback
     */
    public CompletableFuture<Void> getExitCallback() {
        return this.exitCallback;
    }

    /**
     * Stop the ASIO driver
     */
    public void stop() {
        try {
            this.in.close();
            this.out.close();
            this.socket.close();
        } catch (Exception ignored) {

        }

        this.driver.stop();
        this.driver.disposeBuffers();
        this.driver.exit();
        this.driver.shutdownAndUnloadDriver();

        this.exitCallback.complete(null);
    }

    @Override
    public void sampleRateDidChange(double sampleRate) {
        if ((int) sampleRate != this.sampleRate)
            throw new RuntimeException("Sample rate mismatch: " + (int) sampleRate + " != " + this.sampleRate);
    }

    @Override
    public void bufferSizeChanged(int bufferSize) {
        if (bufferSize != this.bufferSize)
            throw new RuntimeException("Buffer size mismatch: " + bufferSize + " != " + this.bufferSize);
    }

    @Override public void resetRequest() {}
    @Override public void resyncRequest() {}
    @Override public void latenciesChanged(int inputLatency, int outputLatency) {}

}
