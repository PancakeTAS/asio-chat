package gay.pancake.asiochat;

import com.synthbot.jasiohost.AsioChannel;
import com.synthbot.jasiohost.AsioDriver;
import com.synthbot.jasiohost.AsioDriverListener;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * asio-chat server
 *
 * @author Pancake
 */
public class Server implements AsioDriverListener {

    /** The ASIO driver */
    private final AsioDriver driver;
    /** The server socket */
    private ServerSocket socket;

    /** The input channel */
    private AsioChannel inputChannel;
    /** The output channels */
    private AsioChannel leftOutputChannel, rightOutputChannel;

    /** The socket */
    private Socket client;
    /** The input stream */
    private DataInputStream in;
    /** The output stream */
    private DataOutputStream out;

    /** The sample rate and buffer size */
    private final int sampleRate, bufferSize;
    /** The buffer */
    private final byte[] buffer;

    /** The exit callback */
    private final CompletableFuture<Void> exitCallback = new CompletableFuture<>();

    /**
     * Create a new server
     *
     * @param asio The name of the ASIO driver to use
     */
    public Server(String asio) {
        this.driver = AsioDriver.getDriver(asio);
        this.driver.addAsioDriverListener(this);

        this.sampleRate = (int) this.driver.getSampleRate();
        this.bufferSize = this.driver.getBufferPreferredSize();
        this.buffer = new byte[this.bufferSize * 4];
    }

    /**
     * Bind the server to a port
     *
     * @param port The port to listen on
     * @throws IOException If the server fails to start
     */
    public void bind(int port) throws IOException {
        this.socket = new ServerSocket(port);
        var client = this.socket.accept();
        this.in = new DataInputStream(client.getInputStream());
        this.out = new DataOutputStream(client.getOutputStream());

        this.out.writeInt(this.bufferSize);
        this.out.writeInt(this.sampleRate);
        this.out.flush();

        this.client = client;
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
            if (this.client.isClosed())
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
            this.client.close();
            this.socket.close();
        } catch (Exception ignored) {

        }

        this.driver.stop();
        this.driver.disposeBuffers();
        this.driver.exit();
        this.driver.shutdownAndUnloadDriver();
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
