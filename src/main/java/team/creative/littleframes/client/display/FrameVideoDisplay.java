package team.creative.littleframes.client.display;

import com.creativemd.creativecore.common.utils.mc.TickUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import team.creative.littleframes.client.texture.TextureCache;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;


public class FrameVideoDisplay extends FrameDisplay {

    private static final String VLC_DOWNLOAD_32 = "https://i.imgur.com/qDIb9iV.png";
    private static final String VLC_DOWNLOAD_64 = "https://i.imgur.com/3EKo7Jx.png";
    private static final int ACCEPTABLE_SYNC_TIME = 1000;
    private static boolean isVLCInstalled = true;


    public static FrameDisplay createVideoDisplay(String url, float volume, boolean loop) {
        try {
            if (isVLCInstalled)
                return new FrameVideoDisplay(url, volume, loop);
        } catch (Exception | UnsatisfiedLinkError e) {
            e.printStackTrace();
        }
        isVLCInstalled = false;
        String failURL = System.getProperty("sun.arch.data.model").equals("32") ? VLC_DOWNLOAD_32 : VLC_DOWNLOAD_64;
        TextureCache cache = TextureCache.get(failURL);
        if (cache.ready())
            return cache.createDisplay(failURL, volume, loop, true);
        return null;
    }

    public int width = 1;
    public int height = 1;
    public CallbackMediaPlayerComponent player;
    public ByteBuffer buffer;
    private int texture;
    private boolean stream = false;
    private float lastSetVolume;
    private AtomicBoolean needsUpdate = new AtomicBoolean(false);
    private boolean first = true;
    private static final MediaPlayerFactory medialPlayerFactory = new MediaPlayerFactory("--quiet");


    public FrameVideoDisplay(String url, float volume, boolean loop) {
        super();
        System.err.println("Constructor XL-0");

        texture = GlStateManager.generateTexture();
        player = new CallbackMediaPlayerComponent(medialPlayerFactory, null, null, false, new RenderCallback() {
            @Override
            public void display(MediaPlayer mediaPlayer, ByteBuffer[] nativeBuffers, BufferFormat bufferFormat) {
                synchronized (FrameVideoDisplay.this) {
                    buffer = nativeBuffers[0];
                    needsUpdate.set(true);
                }
            }
        }, new BufferFormatCallback() {

            @Override
            public BufferFormat getBufferFormat(int sourceWidth, int sourceHeight) {
                synchronized (this) {
                    FrameVideoDisplay.this.width = sourceWidth;
                    FrameVideoDisplay.this.height = sourceHeight;
                    FrameVideoDisplay.this.first = true;
                }
                return new BufferFormat("RGBA", sourceWidth, sourceHeight, new int[]{sourceWidth * 4}, new int[]{sourceHeight});
            }

            @Override
            public void allocatedBuffers(ByteBuffer[] buffers) {

            }

        }, null);

        player.mediaPlayer().audio().setVolume((int) (volume * 100F));
        lastSetVolume = volume;
        player.mediaPlayer().controls().setRepeat(loop);
        player.mediaPlayer().media().start(url);

    }

    @Override
    public void prepare(String url, float volume, boolean playing, boolean loop, int tick) {
        if (player == null) {
            return;
        }
        synchronized (this) {
            if (needsUpdate.getAndSet(false)) {
                if (buffer != null && first) {
                    GlStateManager.pushMatrix();
                    GlStateManager.bindTexture(texture);
                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
                    GlStateManager.popMatrix();
                    first = false;
                    System.err.println("Prepare first = false XL-0");
                } else {
                    GlStateManager.pushMatrix();
                    GlStateManager.bindTexture(texture);
                    GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
                    GlStateManager.popMatrix();
                }
            }
        }
        if (player.mediaPlayer().media().isValid()) {
            boolean realPlaying = playing && !Minecraft.getMinecraft().isGamePaused();

            if (volume != lastSetVolume) {
                player.mediaPlayer().audio().setVolume((int) (volume * 100F));
                lastSetVolume = volume;
            }
            if (player.mediaPlayer().controls().getRepeat() != loop) {
                player.mediaPlayer().controls().setRepeat(loop);
            }
            long tickTime = 50;
            long newDuration = player.mediaPlayer().status().length();
            if (!stream && newDuration != -1 && newDuration != 0 && player.mediaPlayer().media().info().duration() == 0)
                stream = true;
            if (stream) {
                if (player.mediaPlayer().status().isPlaying() != realPlaying) {
                    player.mediaPlayer().controls().setPause(!realPlaying);
                }
            } else {
                if (player.mediaPlayer().status().length() > 0) {
                    long time = tick * tickTime + (realPlaying ? (long) (TickUtils.getPartialTickTime() * tickTime) : 0);
                    if (player.mediaPlayer().status().isSeekable() && time > player.mediaPlayer().status().time())
                        if (loop)
                            time %= player.mediaPlayer().status().length();
                    if (Math.abs(time - player.mediaPlayer().status().time()) > ACCEPTABLE_SYNC_TIME) {
                        long newTime = tick * tickTime + (realPlaying ? (long) (TickUtils.getPartialTickTime() * tickTime) : 0);
                        if (player.mediaPlayer().status().isSeekable() && newTime > player.mediaPlayer().status().length())
                            if (loop)
                                newTime %= player.mediaPlayer().status().length();

                        player.mediaPlayer().controls().setTime(newTime);
                        if (player.mediaPlayer().status().isPlaying() != realPlaying)
                            player.mediaPlayer().controls().setPause(!realPlaying);

                    }
                }
            }
        }
    }

    @Override
    public void release() {

        if (player == null) {
            return;
        }
        player.release();
        player = null;

        GlStateManager.deleteTexture(texture);
        System.err.println("Release XL-0");
        texture = 0;

    }

    @Override
    public int texture() {
        return texture;
    }

    @Override
    public void pause(String url, float volume, boolean playing, boolean loop, int tick) {
        if (player == null) {
            return;
        }
        player.mediaPlayer().controls().setTime(tick * 50);
        player.mediaPlayer().controls().pause();
    }

    @Override
    public void resume(String url, float volume, boolean playing, boolean loop, int tick) {
        if (player == null) {
            return;
        }
        player.mediaPlayer().controls().setTime(tick * 50);
        player.mediaPlayer().controls().play();

    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

}
