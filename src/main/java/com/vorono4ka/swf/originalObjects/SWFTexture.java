package com.vorono4ka.swf.originalObjects;

import com.jogamp.opengl.GL3;
import com.vorono4ka.compression.Decompressor;
import com.vorono4ka.streams.ByteStream;
import com.vorono4ka.editor.renderer.texture.GLImage;
import com.vorono4ka.swf.SupercellSWF;
import com.vorono4ka.swf.constants.Tag;
import com.vorono4ka.swf.exceptions.LoadingFaultException;
import com.vorono4ka.swf.exceptions.TextureFileNotFound;
import com.vorono4ka.utilities.BufferUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Path;

public class SWFTexture extends GLImage implements Savable {
    public static final int TILE_SIZE = 32;

    private int index;

    private Tag tag;

    public SWFTexture() {
        this.index = -1;
    }

    public SWFTexture(Tag tag) {
        this.tag = tag;
        this.index = -1;
    }

    private static byte[] getTextureFileBytes(SupercellSWF swf, String compressedTextureFilename) throws TextureFileNotFound {
        Path compressedTextureFilepath = swf.getPath().getParent().resolve(compressedTextureFilename);
        File file = new File(compressedTextureFilepath.toUri());

        byte[] compressedData;
        try (FileInputStream fis = new FileInputStream(file)) {
            compressedData = fis.readAllBytes();
        } catch (FileNotFoundException e) {
            throw new TextureFileNotFound(compressedTextureFilepath.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return compressedData;
    }

    private static TextureInfo getTextureInfoByType(int type) {
        int pixelFormat = GL3.GL_RGBA;
        int pixelType = GL3.GL_UNSIGNED_BYTE;
        int pixelBytes = 4;

        switch (type) {
            case 2, 8 -> {
                pixelType = GL3.GL_UNSIGNED_SHORT_4_4_4_4;
                pixelBytes = 2;
            }
            case 3 -> {
                pixelType = GL3.GL_UNSIGNED_SHORT_5_5_5_1;
                pixelBytes = 2;
            }
            case 4 -> {
                pixelType = GL3.GL_UNSIGNED_SHORT_5_6_5;
                pixelFormat = GL3.GL_RGB;
                pixelBytes = 2;
            }
            case 6 -> {
                pixelFormat = GL3.GL_LUMINANCE_ALPHA;
                pixelBytes = 2;
            }
            case 10 -> {
                pixelFormat = GL3.GL_LUMINANCE;
                pixelBytes = 1;
            }
        }

        return new TextureInfo(pixelFormat, pixelType, pixelBytes);
    }

    private static boolean isSeparatedByTiles(Tag tag) {
        return tag == Tag.TEXTURE_5 || tag == Tag.TEXTURE_6 || tag == Tag.TEXTURE_7;
    }

    public void load(SupercellSWF swf, Tag tag, boolean hasTexture) throws LoadingFaultException, TextureFileNotFound {
        this.tag = tag;

        int khronosTextureLength = 0;
        if (tag == Tag.KHRONOS_TEXTURE) {
            khronosTextureLength = swf.readInt();
            assert khronosTextureLength > 0;
        }

        String compressedTextureFilename = null;
        if (tag == Tag.COMPRESSED_KHRONOS_TEXTURE) {
            compressedTextureFilename = swf.readAscii();
            if (compressedTextureFilename == null) {
                throw new LoadingFaultException("Compressed texture filename cannot be null.");
            }
        }

        int type = swf.readUnsignedChar();
        this.width = swf.readShort();
        this.height = swf.readShort();

        if (!hasTexture) return;

        TextureInfo textureInfo = getTextureInfoByType(type);

        ByteBuffer ktxData = null;
        Buffer pixels = null;
        switch (tag) {
            case KHRONOS_TEXTURE -> {
                byte[] bytes = swf.readByteArray(khronosTextureLength);
                ktxData = BufferUtils.wrapDirect(bytes);
            }
            case COMPRESSED_KHRONOS_TEXTURE -> {
                byte[] compressedData = getTextureFileBytes(swf, compressedTextureFilename);
                byte[] decompressed = Decompressor.decompressZstd(compressedData, 0);
                ktxData = BufferUtils.wrapDirect(decompressed);
            }
            default ->
                pixels = this.loadTexture(swf, this.width, this.height, textureInfo.pixelBytes(), isSeparatedByTiles(tag));
        }

        this.createWithFormat(ktxData, false, tag.getTextureFilter(), this.width, this.height, pixels, textureInfo.pixelFormat(), textureInfo.pixelType());
    }

    @Override
    public void save(ByteStream stream) {
        stream.writeUnsignedChar(0);  // TODO: calculate type
        stream.writeShort(this.width);
        stream.writeShort(this.height);
    }

    public int getIndex() {
        return this.index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public Tag getTag() {
        return tag;
    }

    public void setTag(Tag tag) {
        this.tag = tag;
    }

    private Buffer loadTexture(SupercellSWF swf, int width, int height, int pixelBytes, boolean separatedByTiles) {
        return switch (pixelBytes) {
            case 1 -> this.loadTextureAsChar(swf, width, height, separatedByTiles);
            case 2 -> this.loadTextureAsShort(swf, width, height, separatedByTiles);
            case 4 -> this.loadTextureAsInt(swf, width, height, separatedByTiles);
            default ->
                throw new IllegalStateException("Unexpected value: " + pixelBytes);
        };
    }

    private ByteBuffer loadTextureAsChar(SupercellSWF swf, int width, int height, boolean separatedByTiles) {
        if (separatedByTiles) {
            int xChunksCount = width / TILE_SIZE;
            int yChunksCount = height / TILE_SIZE;

            ByteBuffer pixels = BufferUtils.allocateDirect(width * height);

            for (int tileY = 0; tileY < yChunksCount + 1; tileY++) {
                for (int tileX = 0; tileX < xChunksCount + 1; tileX++) {
                    int tileWidth = Math.min(width - (tileX * TILE_SIZE), TILE_SIZE);
                    int tileHeight = Math.min(height - (tileY * TILE_SIZE), TILE_SIZE);

                    byte[] tilePixels = readTileAsChar(swf, tileWidth, tileHeight);

                    for (int y = 0; y < tileHeight; y++) {
                        int pixelY = (tileY * TILE_SIZE) + y;

                        for (int x = 0; x < tileWidth; x++) {
                            int pixelX = (tileX * TILE_SIZE) + x;

                            pixels.put(pixelY * width + pixelX, tilePixels[y * tileWidth + x]);
                        }
                    }
                }
            }

            return pixels;
        } else {
            return BufferUtils.wrapDirect(swf.readByteArray(width * height));
        }
    }

    private ShortBuffer loadTextureAsShort(SupercellSWF swf, int width, int height, boolean separatedByTiles) {
        if (separatedByTiles) {
            int xChunksCount = width / TILE_SIZE;
            int yChunksCount = height / TILE_SIZE;

            ShortBuffer pixels = BufferUtils.allocateDirect(width * height * Short.BYTES).asShortBuffer();

            for (int tileY = 0; tileY < yChunksCount + 1; tileY++) {
                for (int tileX = 0; tileX < xChunksCount + 1; tileX++) {
                    int tileWidth = Math.min(width - (tileX * TILE_SIZE), TILE_SIZE);
                    int tileHeight = Math.min(height - (tileY * TILE_SIZE), TILE_SIZE);

                    short[] tilePixels = readTileAsShort(swf, tileWidth, tileHeight);

                    for (int y = 0; y < tileHeight; y++) {
                        int pixelY = (tileY * TILE_SIZE) + y;

                        for (int x = 0; x < tileWidth; x++) {
                            int pixelX = (tileX * TILE_SIZE) + x;

                            pixels.put(pixelY * width + pixelX, tilePixels[y * tileWidth + x]);
                        }
                    }
                }
            }

            return pixels;
        } else {
            return BufferUtils.wrapDirect(swf.readShortArray(width * height));
        }
    }

    private IntBuffer loadTextureAsInt(SupercellSWF swf, int width, int height, boolean separatedByTiles) {
        if (separatedByTiles) {
            int xChunksCount = width / TILE_SIZE;
            int yChunksCount = height / TILE_SIZE;

            IntBuffer pixels = BufferUtils.allocateDirect(width * height * Integer.BYTES).asIntBuffer();

            for (int tileY = 0; tileY < yChunksCount + 1; tileY++) {
                for (int tileX = 0; tileX < xChunksCount + 1; tileX++) {
                    int tileWidth = Math.min(width - (tileX * TILE_SIZE), TILE_SIZE);
                    int tileHeight = Math.min(height - (tileY * TILE_SIZE), TILE_SIZE);

                    int[] tilePixels = readTileAsInt(swf, tileWidth, tileHeight);

                    for (int y = 0; y < tileHeight; y++) {
                        int pixelY = (tileY * TILE_SIZE) + y;

                        for (int x = 0; x < tileWidth; x++) {
                            int pixelX = (tileX * TILE_SIZE) + x;

                            pixels.put(pixelY * width + pixelX, tilePixels[y * tileWidth + x]);
                        }
                    }
                }
            }

            return pixels;
        } else {
            return BufferUtils.wrapDirect(swf.readIntArray(width * height));
        }
    }

    private byte[] readTileAsChar(SupercellSWF swf, int width, int height) {
        return swf.readByteArray(width * height);
    }

    private short[] readTileAsShort(SupercellSWF swf, int width, int height) {
        return swf.readShortArray(width * height);
    }

    private int[] readTileAsInt(SupercellSWF swf, int width, int height) {
        return swf.readIntArray(width * height);
    }

    private record TextureInfo(int pixelFormat, int pixelType, int pixelBytes) {
    }
}
