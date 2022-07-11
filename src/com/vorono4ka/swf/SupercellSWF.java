package com.vorono4ka.swf;

import com.vorono4ka.compression.Decompressor;
import com.vorono4ka.compression.exceptions.UnknownFileMagicException;
import com.vorono4ka.compression.exceptions.UnknownFileVersionException;
import com.vorono4ka.resources.ResourceManager;
import com.vorono4ka.streams.ByteStream;
import com.vorono4ka.swf.constants.Tag;
import com.vorono4ka.swf.displayObjects.original.*;
import com.vorono4ka.swf.exceptions.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SupercellSWF {
    public static final String TEXTURE_EXTENSION = "_tex.sc";

    private ByteStream stream;

    private int shapesCount;
    private int movieClipsCount;
    private int texturesCount;
    private int textFieldsCount;
    private int movieClipModifiersCount;

    private final List<String> fontsNames;
    private final List<ScMatrixBank> matrixBanks;

    private int exportsCount;
    private short[] exportsIds;
    private String[] exportsNames;

    private SWFTexture[] textures;
    private ShapeOriginal[] shapes;
    private int[] shapesIds;
    private MovieClipOriginal[] movieClips;
    private int[] movieClipsIds;
    private TextFieldOriginal[] textFields;
    private int[] textFieldsIds;

    private MovieClipModifierOriginal[] movieClipModifiers;

    private boolean useLowresTexture;
    private boolean useUncommonResolution;
    private boolean useExternalTexture;
    private boolean useHighresTexture;
    private String uncommonResolutionTexturePath;

    private String filename;

    public SupercellSWF() {
        this.matrixBanks = new ArrayList<>();
        this.fontsNames = new ArrayList<>();
    }

    public boolean load(String path, String filename) throws LoadingFaultException, UnableToFindObjectException {
        this.filename = filename;

        if (this.loadInternal(path, false)) {
            if (!this.useExternalTexture) return true;

            if (this.useUncommonResolution) {
                path = this.uncommonResolutionTexturePath;
            } else {
                path = path.substring(0, path.length() - 3) + TEXTURE_EXTENSION;
            }

            return this.loadInternal(path, true);
        }

        return false;
    }

    private boolean loadInternal(String path, boolean isTextureFile) throws LoadingFaultException, UnableToFindObjectException {
        byte[] data;

        File file = new File(path);

        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            data = fileInputStream.readAllBytes();
            fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        byte[] decompressedData;

        try {
            decompressedData = Decompressor.decompress(data);
        } catch (UnknownFileMagicException | UnknownFileVersionException | IOException e) {
            e.printStackTrace();
            return false;
        }

        this.stream = new ByteStream(decompressedData);

        if (isTextureFile) {
            return this.loadTags(true, path);
        }

        this.shapesCount = this.readShort();
        this.movieClipsCount = this.readShort();
        this.texturesCount = this.readShort();
        this.textFieldsCount = this.readShort();
        int matricesCount = this.readShort();
        int colorTransformsCount = this.readShort();

        ScMatrixBank matrixBank = new ScMatrixBank();
        matrixBank.init(matricesCount, colorTransformsCount);
        this.matrixBanks.add(matrixBank);

        this.skip(5);

        this.exportsCount = this.readShort();
        this.exportsIds = this.readShortArray(this.exportsCount);

        this.exportsNames = new String[this.exportsCount];
        for (int i = 0; i < this.exportsCount; i++) {
            this.exportsNames[i] = this.readAscii();
        }

        this.shapesIds = new int[this.shapesCount];
        this.movieClipsIds = new int[this.movieClipsCount];
        this.textFieldsIds = new int[this.textFieldsCount];

        this.shapes = new ShapeOriginal[this.shapesCount];
        for (int i = 0; i < this.shapesCount; i++) {
            this.shapes[i] = new ShapeOriginal();
        }

        this.movieClips = new MovieClipOriginal[this.movieClipsCount];
        for (int i = 0; i < this.movieClipsCount; i++) {
            this.movieClips[i] = new MovieClipOriginal();
        }

        this.textures = new SWFTexture[this.texturesCount];
        for (int i = 0; i < this.texturesCount; i++) {
            this.textures[i] = new SWFTexture();
        }

        this.textFields = new TextFieldOriginal[this.textFieldsCount];
        for (int i = 0; i < this.textFieldsCount; i++) {
            this.textFields[i] = new TextFieldOriginal();
        }

        if (this.loadTags(false, path)) {
            if (this.exportsCount == 0) return true;

            for (int i = 0; i < this.exportsCount; i++) {
                String exportName = this.exportsNames[i];
                MovieClipOriginal movieClip = this.getOriginalMovieClip(this.exportsIds[i], exportName);
                movieClip.setExportName(exportName);
            }

            return true;
        }
        return false;
    }

    private boolean loadTags(boolean isTextureFile, String path) throws LoadingFaultException {
        String highresSuffix = "_highres";
        String lowresSuffix = "_lowres";

        ScMatrixBank matrixBank = this.matrixBanks.get(0);

        int loadedShapes = 0;
        int loadedMovieClips = 0;
        int loadedTextures = 0;
        int loadedTextFields = 0;
        int loadedMatrices = 0;
        int loadedColorTransforms = 0;

        int loadedMovieClipsModifiers = 0;

        while (true) {
            int tag = this.readUnsignedChar();
            int length = this.readInt();

            if (length < 0) {
                throw new NegativeTagLengthException(String.format("Negative tag length. Tag %d, %s", tag, this.filename));
            }

            if (tag > Tag.values().length) {
                try {
                    throw new UnsupportedTagException(String.format("Encountered unknown tag %d, %s", tag, this.filename));
                } catch (UnsupportedTagException exception) {
                    exception.printStackTrace();
                }

                if (length > 0) {
                    this.skip(length);
                    continue;
                }
            }

            Tag tagValue = Tag.values()[tag];
            switch (tagValue) {
                case EOF -> {
                    if (isTextureFile) {
                        if (loadedTextures != this.texturesCount) {
                            throw new LoadingFaultException(String.format("Texture count in .sc and _tex.sc doesn't match: %s", this.filename));
                        }
                    } else {
                        if (loadedMatrices != matrixBank.getMatricesCount() ||
                            loadedColorTransforms != matrixBank.getColorTransformsCount() ||
                            loadedMovieClips != this.movieClipsCount ||
                            loadedShapes != this.shapesCount ||
                            loadedTextFields != this.textFieldsCount) {
                            throw new LoadingFaultException("Didn't load whole .sc properly. ");
                        }
                    }

                    return true;
                }
                case TEXTURE, TEXTURE_2, TEXTURE_3, TEXTURE_4, TEXTURE_5, TEXTURE_6, TEXTURE_7, TEXTURE_8 -> {
                    if (loadedTextures >= this.texturesCount) {
                        throw new TooManyObjectsException("Trying to load too many textures from ");
                    }
                    this.textures[loadedTextures++].load(this, tagValue, isTextureFile);
                }
                case SHAPE, SHAPE_2 -> {
                    if (loadedShapes >= this.shapesCount) {
                        throw new TooManyObjectsException("Trying to load too many shapes from ");
                    }
                    this.shapesIds[loadedShapes] = this.shapes[loadedShapes++].load(this, tagValue);
                }
                case MOVIE_CLIP, MOVIE_CLIP_2, MOVIE_CLIP_3, MOVIE_CLIP_4, MOVIE_CLIP_35 -> {
                    if (loadedMovieClips >= this.movieClipsCount) {
                        throw new TooManyObjectsException("Trying to load too many MovieClips from ");
                    }
                    this.movieClipsIds[loadedMovieClips] = this.movieClips[loadedMovieClips++].load(this, tagValue);
                }
                case TEXT_FIELD, TEXT_FIELD_2, TEXT_FIELD_3, TEXT_FIELD_4, TEXT_FIELD_5, TEXT_FIELD_6, TEXT_FIELD_7, TEXT_FIELD_8 -> {
                    if (loadedTextFields >= this.textFieldsCount) {
                        throw new TooManyObjectsException("Trying to load too many TextFields from ");
                    }

                    int offset = this.stream.getOffset();
                    this.textFieldsIds[loadedTextFields] = this.textFields[loadedTextFields++].load(this, tagValue);
                }
                case MATRIX -> matrixBank.getMatrix(loadedMatrices++).read(this);
                case COLOR_TRANSFORM -> matrixBank.getColorTransforms(loadedColorTransforms++).read(this.stream);
                case TAG_TIMELINE_INDEXES -> {
                    try {
                        throw new UnsupportedTagException("TAG_TIMELINE_INDEXES no longer in use");
                    } catch (UnsupportedTagException e) {
                        e.printStackTrace();
                    }

                    int indexesLength = this.readInt();
                    this.skip(indexesLength);
                }
                case USE_LOWRES_TEXTURE -> this.useLowresTexture = true;
                case USE_EXTERNAL_TEXTURE -> this.useExternalTexture = true;
                case USE_UNCOMMON_RESOLUTION -> {
                    this.useHighresTexture = true;
                    this.useUncommonResolution = true;

                    String withoutExtension = path.substring(0, path.length() - 3);
                    String highresPath = withoutExtension + highresSuffix + TEXTURE_EXTENSION;
                    String lowresPath = withoutExtension + lowresSuffix + TEXTURE_EXTENSION;

                    String uncommonPath = highresPath;
                    if (!ResourceManager.doesFileExist(highresPath)) {
                        if (ResourceManager.doesFileExist(lowresPath)) {
                            uncommonPath = lowresPath;
                            this.useLowresTexture = true;
                        }
                    }

                    this.uncommonResolutionTexturePath = uncommonPath;
                }
                case EXTERNAL_FILES_SUFFIXES -> {
                    highresSuffix = this.readAscii();
                    lowresSuffix = this.readAscii();
                }
                case MATRIX_PRECISE -> matrixBank.getMatrix(loadedMatrices++).readPrecise(this);
                case MOVIE_CLIP_MODIFIERS -> {
                    this.movieClipModifiersCount = this.readShort();
                    this.movieClipModifiers = new MovieClipModifierOriginal[this.movieClipModifiersCount];

                    for (int i = 0; i < this.movieClipModifiersCount; i++) {
                        this.movieClipModifiers[i] = new MovieClipModifierOriginal();
                    }
                }
                case MODIFIER_STATE_2, MODIFIER_STATE_3, MODIFIER_STATE_4 -> this.movieClipModifiers[loadedMovieClipsModifiers++].load(this, tagValue);
                case EXTRA_MATRIX_BANK -> {
                    int matricesCount = this.readShort();
                    int colorTransformsCount = this.readShort();

                    matrixBank = new ScMatrixBank();
                    matrixBank.init(matricesCount, colorTransformsCount);
                    this.matrixBanks.add(matrixBank);

                    loadedMatrices = 0;
                    loadedColorTransforms = 0;
                }
                default -> {
                    try {
                        throw new UnsupportedTagException(String.format("Encountered unknown tag %d, %s", tag, this.filename));
                    } catch (UnsupportedTagException exception) {
                        exception.printStackTrace();
                    }

                    if (length > 0) {
                        this.skip(length);
                    }
                }
            }
        }
    }

    public MovieClipOriginal getOriginalMovieClip(int id, String name) throws UnableToFindObjectException {
        for (int i = 0; i < this.movieClipsCount; i++) {
            if (this.movieClipsIds[i] == id) {
                return this.movieClips[i];
            }
        }
        
        String message = String.format("Unable to find some MovieClip id from %s", this.filename);
        if (name != null) {
            message += String.format(" needed by export name %s", name);
        }

        throw new UnableToFindObjectException(message);
    }

    public DisplayObjectOriginal getOriginalDisplayObject(int id, String name) throws UnableToFindObjectException {
        for (int i = 0; i < this.shapesCount; i++) {
            if (this.shapesIds[i] == id) {
                return this.shapes[i];
            }
        }

        for (int i = 0; i < this.movieClipsCount; i++) {
            if (this.movieClipsIds[i] == id) {
                return this.movieClips[i];
            }
        }

        for (int i = 0; i < this.textFieldsCount; i++) {
            if (this.textFieldsIds[i] == id) {
                return this.textFields[i];
            }
        }

        for (int i = 0; i < this.movieClipModifiersCount; i++) {
            if (this.movieClipModifiers[i].getId() == id) {
                return this.movieClipModifiers[i];
            }
        }

        String message = String.format("Unable to find some DisplayObject id %d, %s", id, this.filename);
        if (name != null) {
            message += String.format(" needed by export name %s", name);
        }

        throw new UnableToFindObjectException(message);
    }

    public int getShapesCount() {
        return shapesCount;
    }

    public int getMovieClipsCount() {
        return movieClipsCount;
    }

    public int getTexturesCount() {
        return texturesCount;
    }

    public int getTextFieldsCount() {
        return textFieldsCount;
    }

    public int getMovieClipModifiersCount() {
        return movieClipModifiersCount;
    }

    public int getExportsCount() {
        return exportsCount;
    }

    public int[] getShapesIds() {
        return shapesIds;
    }

    public int[] getMovieClipsIds() {
        return movieClipsIds;
    }

    public int[] getTextFieldsIds() {
        return textFieldsIds;
    }

    public String[] getExportsNames() {
        return exportsNames;
    }

    public ScMatrixBank getMatrixBank(int index) {
        return this.matrixBanks.get(index);
    }

    public SWFTexture getTexture(int textureId) {
        return this.textures[textureId];
    }

    public String getFilename() {
        return filename;
    }

    public int readUnsignedChar() {
        return this.stream.readUnsignedChar();
    }

    public int readShort() {
        return this.stream.readShort();
    }

    public int readInt() {
        return this.stream.readInt();
    }

    public int readTwip() {
        return this.stream.readInt() / 20;
    }

    public boolean readBoolean() {
        return this.stream.readBoolean();
    }

    public byte[] readByteArray(int count) {
        return this.stream.readByteArray(count);
    }

    public short[] readShortArray(int count) {
        return this.stream.readShortArray(count);
    }

    public int[] readIntArray(int count) {
        return this.stream.readIntArray(count);
    }

    public String readAscii() {
        int length = this.readUnsignedChar() & 0xff;
        if (length == 255) return null;

        return new String(this.stream.read(length), StandardCharsets.UTF_8);
    }

    public String readFontName() {
        String fontName = this.readAscii();
        if (fontName != null) {
            if (!this.fontsNames.contains(fontName)) {
                this.fontsNames.add(fontName);
            }
        }

        return fontName;
    }

    public void skip(int count) {
        this.stream.skip(count);
    }
}
