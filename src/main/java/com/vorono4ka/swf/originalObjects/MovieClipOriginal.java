package com.vorono4ka.swf.originalObjects;

import com.vorono4ka.math.MathHelper;
import com.vorono4ka.math.Rect;
import com.vorono4ka.streams.ByteStream;
import com.vorono4ka.swf.MovieClipFrame;
import com.vorono4ka.swf.MovieClipFrameElement;
import com.vorono4ka.swf.SupercellSWF;
import com.vorono4ka.swf.constants.Tag;
import com.vorono4ka.swf.displayObjects.DisplayObject;
import com.vorono4ka.swf.displayObjects.MovieClip;
import com.vorono4ka.swf.exceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class MovieClipOriginal extends DisplayObjectOriginal {
    private static final Logger LOGGER = LoggerFactory.getLogger(MovieClipOriginal.class);

    private int fps;
    private short[] frameElements;
    private MovieClipFrame[] frames;

    private int childrenCount;
    private short[] childrenIds;
    private byte[] childrenBlends;
    private String[] childrenNames;

    private Rect scalingGrid;
    private int matrixBankIndex;

    private String exportName;
    private DisplayObjectOriginal[] children;

    public int load(SupercellSWF swf, Tag tag) throws LoadingFaultException, UnsupportedCustomPropertyException {
        this.tag = tag;

        this.id = swf.readShort();
        this.fps = swf.readUnsignedChar();
        // *(a1 + 54) = *(a1 + 54) & 0xFF80 | ZN12SupercellSWF16readUnsignedCharEv(a2) & 0x7F;

        int framesCount = swf.readShort();
        this.frames = new MovieClipFrame[framesCount];
        for (int i = 0; i < this.frames.length; i++) {
            this.frames[i] = new MovieClipFrame();
        }

        if (tag.hasCustomProperties()) {
            int propertyCount = swf.readUnsignedChar();
            for (int i = 0; i < propertyCount; i++) {
                int propertyType = swf.readUnsignedChar();
                switch (propertyType) {
                    case 0 -> {
                        boolean unknown = swf.readBoolean();
                        // *(a1 + 54) = *(a1 + 54) & 0xFF7F | (unknown ? 128 : 0);
                    }
                    default ->
                        throw new UnsupportedCustomPropertyException("Unsupported custom property type: " + propertyType);
                }
            }
        }

        switch (Objects.requireNonNull(tag)) {
            case MOVIE_CLIP -> {
            }  // TAG_MOVIE_CLIP no longer supported
            case MOVIE_CLIP_4 -> {
                try {
                    throw new UnsupportedTagException("TAG_MOVIE_CLIP_4 no longer supported\n");
                } catch (UnsupportedTagException exception) {
                    LOGGER.error(exception.getMessage(), exception);
                }
            }
            default -> {
                int elementsCount = swf.readInt();
                this.frameElements = swf.readShortArray(elementsCount * 3);
            }
        }

        this.childrenCount = swf.readShort();
        this.childrenIds = swf.readShortArray(this.childrenCount);

        if (tag.hasBlendData()) {
            this.childrenBlends = swf.readByteArray(this.childrenCount);
        } else {
            this.childrenBlends = new byte[this.childrenCount];
            Arrays.fill(this.childrenBlends, (byte) 0);
        }

        this.childrenNames = new String[this.childrenCount];
        for (int i = 0; i < this.childrenCount; i++) {
            this.childrenNames[i] = swf.readAscii();
        }

        int loadedCommands = 0;
        int usedElements = 0;

        while (true) {
            int frameTag = swf.readUnsignedChar();
            int length = swf.readInt();

            if (length < 0) {
                throw new NegativeTagLengthException(String.format("Negative tag length in MovieClip. Tag %d, %s", frameTag, swf.getFilename()));
            }

            Tag tagValue = Tag.values()[frameTag];
            switch (tagValue) {
                case EOF -> {
                    return this.id;
                }
                case MOVIE_CLIP_FRAME,
                     MOVIE_CLIP_FRAME_2 -> {  // TAG_MOVIE_CLIP_FRAME no longer supported
                    MovieClipFrame frame = this.frames[loadedCommands++];
                    int elementsCount = frame.load(swf, tagValue);

                    if (tagValue != Tag.MOVIE_CLIP_FRAME) {
                        MovieClipFrameElement[] elements = new MovieClipFrameElement[elementsCount];
                        for (int i = 0; i < elementsCount; i++) {
                            elements[i] = new MovieClipFrameElement(
                                this.frameElements[usedElements * 3] & 0xFFFF,
                                this.frameElements[usedElements * 3 + 1] & 0xFFFF,
                                this.frameElements[usedElements * 3 + 2] & 0xFFFF
                            );

                            usedElements++;
                        }
                        frame.setElements(elements);
                    }
                }
                case SCALING_GRID -> {
                    if (this.scalingGrid != null) {
                        throw new LoadingFaultException("multiple scaling grids");
                    }

                    float left = swf.readTwip();
                    float top = swf.readTwip();
                    float width = swf.readTwip();
                    float height = swf.readTwip();
                    float right = MathHelper.round(left + width, 2);
                    float bottom = MathHelper.round(top + height, 2);

                    this.scalingGrid = new Rect(left, top, right, bottom);
                }
                case
                    MATRIX_BANK_INDEX -> // (a1 + 54) & 0x80FF | ((ZN12SupercellSWF16readUnsignedCharEv(a2) & 0x7F) << 8);
                    this.matrixBankIndex = swf.readUnsignedChar();
                default -> {
                    try {
                        throw new UnsupportedTagException(String.format("Unknown tag %d in MovieClip, %s", frameTag, swf.getFilename()));
                    } catch (UnsupportedTagException exception) {
                        LOGGER.error(exception.getMessage(), exception);
                    }
                }
            }
        }
    }

    @Override
    public void save(ByteStream stream) {
        stream.writeShort(this.id);
        stream.writeUnsignedChar(this.fps);

        stream.writeShort(this.frames.length);

        List<MovieClipFrameElement> frameElements = new ArrayList<>();
        for (MovieClipFrame frame : this.frames) {
            Collections.addAll(frameElements, frame.getElements());
        }

        stream.writeInt(frameElements.size());
        for (MovieClipFrameElement element : frameElements) {
            stream.writeShort(element.getChildIndex());
            stream.writeShort(element.getMatrixIndex());
            stream.writeShort(element.getColorTransformIndex());
        }

        stream.writeShort(this.childrenIds.length);
        for (short id : this.childrenIds) {
            stream.writeShort(id);
        }

        if (this.tag == Tag.MOVIE_CLIP_3 || this.tag == Tag.MOVIE_CLIP_5) {
            for (byte blend : this.childrenBlends) {
                stream.writeUnsignedChar(blend);
            }
        }

        for (String name : this.childrenNames) {
            stream.writeAscii(name);
        }

        for (MovieClipFrame frame : this.frames) {
            stream.writeBlock(Tag.MOVIE_CLIP_FRAME_2, frame::save);
        }

        if (this.scalingGrid != null) {
            stream.writeBlock(Tag.SCALING_GRID, blockStream -> {
                blockStream.writeTwip(this.scalingGrid.getLeft());
                blockStream.writeTwip(this.scalingGrid.getTop());
                blockStream.writeTwip(this.scalingGrid.getWidth());
                blockStream.writeTwip(this.scalingGrid.getHeight());
            });
        }

        if (this.matrixBankIndex != 0) {
            stream.writeBlock(Tag.MATRIX_BANK_INDEX, blockStream -> blockStream.writeUnsignedChar(this.matrixBankIndex));
        }

        stream.writeBlock(Tag.EOF, ignored -> {
        });
    }

    public DisplayObject clone(SupercellSWF swf, Rect scalingGrid) throws UnableToFindObjectException {
        return MovieClip.createMovieClip(this, swf, scalingGrid);
    }

    public void createTimelineChildren(SupercellSWF swf) throws UnableToFindObjectException {
        if (this.children == null) {
            this.children = new DisplayObjectOriginal[this.childrenCount];
            for (int i = 0; i < this.childrenCount; i++) {
                this.children[i] = swf.getOriginalDisplayObject(this.childrenIds[i] & 0xFFFF, this.exportName);
            }
        }
    }

    public int getFps() {
        return fps;
    }

    public MovieClipFrame[] getFrames() {
        return frames;
    }

    public int getChildrenCount() {
        return childrenCount;
    }

    public byte[] getChildrenBlends() {
        return childrenBlends;
    }

    public short[] getChildrenIds() {
        return childrenIds;
    }

    public String[] getChildrenNames() {
        return childrenNames;
    }

    public Rect getScalingGrid() {
        return scalingGrid;
    }

    public int getMatrixBankIndex() {
        return matrixBankIndex;
    }

    public DisplayObjectOriginal[] getChildren() {
        return children;
    }

    public String getExportName() {
        return exportName;
    }

    public void setExportName(String exportName) {
        this.exportName = exportName;
    }
}
