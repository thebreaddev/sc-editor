package com.vorono4ka.swf.displayObjects;

import com.vorono4ka.editor.renderer.Stage;
import com.vorono4ka.math.Point;
import com.vorono4ka.math.Rect;
import com.vorono4ka.streams.ByteStream;
import com.vorono4ka.swf.ColorTransform;
import com.vorono4ka.editor.renderer.texture.GLImage;
import com.vorono4ka.swf.Matrix2x3;
import com.vorono4ka.swf.SupercellSWF;
import com.vorono4ka.swf.constants.Tag;
import com.vorono4ka.swf.originalObjects.SWFTexture;

import java.util.Arrays;

public class ShapeDrawBitmapCommand {
    private Tag tag;

    private int vertexCount;
    private Point[] shapePoints;
    private Point[] sheetPoints;

    private GLImage image;

    public void load(SupercellSWF swf, Tag tag) {
        this.tag = tag;

        int textureId = swf.readUnsignedChar();

        this.vertexCount = 4;
        if (tag != Tag.SHAPE_DRAW_BITMAP_COMMAND) {
            this.vertexCount = swf.readUnsignedChar();
        }

        this.image = swf.getTexture(textureId);

        this.shapePoints = new Point[this.vertexCount];
        for (int i = 0; i < this.vertexCount; i++) {
            float x = swf.readTwip();
            float y = swf.readTwip();

            this.shapePoints[i] = new Point(x, y);
        }

        this.sheetPoints = new Point[this.vertexCount];
        for (int i = 0; i < this.vertexCount; i++) {
            float u = swf.readShort();
            float v = swf.readShort();

            if (tag == Tag.SHAPE_DRAW_BITMAP_COMMAND) {
                u *= 65535f / this.image.getWidth();
                v *= 65535f / this.image.getHeight();
            } else if (tag != Tag.SHAPE_DRAW_BITMAP_COMMAND_3) {
                u /= 65535f * this.image.getWidth();
                v /= 65535f * this.image.getHeight();
            }

            this.sheetPoints[i] = new Point(u, v);
        }
    }

    public void save(ByteStream stream) {
        stream.writeUnsignedChar(((SWFTexture) this.image).getIndex());

        if (this.tag != Tag.SHAPE_DRAW_BITMAP_COMMAND) {
            stream.writeUnsignedChar(this.vertexCount);
        }

        for (Point point : this.shapePoints) {
            stream.writeTwip(point.getX());
            stream.writeTwip(point.getY());
        }

        for (Point point : this.sheetPoints) {
            float u = point.getX();
            float v = point.getY();

            if (this.tag != Tag.SHAPE_DRAW_BITMAP_COMMAND_3) {
                u *= 65535f / this.image.getWidth();
                v *= 65535f / this.image.getHeight();
            }

            stream.writeShort((int) u);
            stream.writeShort((int) v);
        }
    }

    public boolean render(Stage stage, Matrix2x3 matrix, ColorTransform colorTransform, int renderConfigBits) {
        Rect bounds = new Rect();

        float[] transformedPoints = new float[this.vertexCount * 2];
        for (int i = 0; i < this.vertexCount; i++) {
            float x = matrix.applyX(this.getX(i), this.getY(i));
            float y = matrix.applyY(this.getX(i), this.getY(i));

            transformedPoints[i * 2] = x;
            transformedPoints[i * 2 + 1] = y;

            if (i == 0) {
                bounds = new Rect(x, y, x, y);
                continue;
            }

            bounds.addPoint(x, y);
        }

        int trianglesCount = this.vertexCount - 2;
        int[] indices = getIndices(trianglesCount);

        if (stage.startShape(bounds, this.image.getTexture(), renderConfigBits)) {
            stage.addTriangles(trianglesCount, indices);

            float redMultiplier = colorTransform.getRedMultiplier() / 255f;
            float greenMultiplier = colorTransform.getGreenMultiplier() / 255f;
            float blueMultiplier = colorTransform.getBlueMultiplier() / 255f;
            float redAddition = colorTransform.getRedAddition() / 255f;
            float greenAddition = colorTransform.getGreenAddition() / 255f;
            float blueAddition = colorTransform.getBlueAddition() / 255f;
            float alpha = colorTransform.getAlpha() / 255f;

            for (int i = 0; i < this.vertexCount; i++) {
                stage.addVertex(
                    transformedPoints[i * 2],
                    transformedPoints[i * 2 + 1],
                    this.getU(i),
                    this.getV(i),
                    redMultiplier,
                    greenMultiplier,
                    blueMultiplier,
                    alpha,
                    redAddition,
                    greenAddition,
                    blueAddition
                );
            }

            return true;
        }

        return false;
    }

    public boolean render9Slice(Stage stage, Matrix2x3 matrix, ColorTransform colorTransform, int renderConfigBits, Rect safeArea, Rect shapeBounds, float width, float height) {
        Rect bounds = new Rect();

        float[] transformedPoints = new float[this.vertexCount * 2];
        for (int i = 0; i < this.vertexCount; i++) {
            float x = this.getX(i);
            if (x <= safeArea.getLeft()) {
                x = Math.min(safeArea.getMidX(), shapeBounds.getLeft() + (x - shapeBounds.getLeft()) * width);
            } else if (x >= safeArea.getRight()) {
                x = Math.max(safeArea.getMidX(), shapeBounds.getRight() + (x - shapeBounds.getRight()) * width);
            }

            float y = this.getY(i);
            if (y <= safeArea.getTop()) {
                y = Math.min(safeArea.getMidY(), shapeBounds.getTop() + (y - shapeBounds.getTop()) * height);
            } else if (y >= safeArea.getBottom()) {
                y = Math.max(safeArea.getMidY(), shapeBounds.getBottom() + (y - shapeBounds.getBottom()) * height);
            }

            float appliedX = matrix.applyX(x, y);
            float appliedY = matrix.applyY(x, y);

            transformedPoints[i * 2] = appliedX;
            transformedPoints[i * 2 + 1] = appliedY;

            if (i == 0) {
                bounds = new Rect(appliedX, appliedY, appliedX, appliedY);
                continue;
            }

            bounds.addPoint(appliedX, appliedY);
        }

        int trianglesCount = this.vertexCount - 2;
        int[] indices = getIndices(trianglesCount);

        if (stage.startShape(bounds, this.image.getTexture(), renderConfigBits)) {
            stage.addTriangles(trianglesCount, indices);

            float redMultiplier = colorTransform.getRedMultiplier() / 255f;
            float greenMultiplier = colorTransform.getGreenMultiplier() / 255f;
            float blueMultiplier = colorTransform.getBlueMultiplier() / 255f;
            float redAddition = colorTransform.getRedAddition() / 255f;
            float greenAddition = colorTransform.getGreenAddition() / 255f;
            float blueAddition = colorTransform.getBlueAddition() / 255f;
            float alpha = colorTransform.getAlpha() / 255f;

            // TODO: optimize vertices and pass color transform via uniforms instead
            for (int i = 0; i < this.vertexCount; i++) {
                stage.addVertex(
                    transformedPoints[i * 2],
                    transformedPoints[i * 2 + 1],
                    this.getU(i),
                    this.getV(i),
                    redMultiplier,
                    greenMultiplier,
                    blueMultiplier,
                    alpha,
                    redAddition,
                    greenAddition,
                    blueAddition
                );
            }

            return true;
        }

        return false;
    }

    public boolean collisionRender(Stage stage, Matrix2x3 matrix, ColorTransform colorTransform) {
        return this.render(stage, matrix, colorTransform, 0);
    }

    public boolean renderUV(Stage stage, int renderConfigBits) {
        Rect bounds = new Rect();

        float[] transformedPoints = new float[this.vertexCount * 2];
        for (int i = 0; i < this.vertexCount; i++) {
            float x = this.getU(i) * this.image.getWidth() - this.image.getWidth() / 2f;
            float y = this.getV(i) * this.image.getHeight() - this.image.getHeight() / 2f;

            transformedPoints[i * 2] = x;
            transformedPoints[i * 2 + 1] = y;

            if (i == 0) {
                bounds = new Rect(x, y, x, y);
                continue;
            }

            bounds.addPoint(x, y);
        }

        int trianglesCount = this.vertexCount - 2;
        int[] indices = getIndices(trianglesCount);

        if (stage.startShape(bounds, stage.getGradientTexture().getTexture(), renderConfigBits)) {
            stage.addTriangles(trianglesCount, indices);

            for (int i = 0; i < this.vertexCount; i++) {
                stage.addVertex(transformedPoints[i * 2], transformedPoints[i * 2 + 1], 1f, 0, 1, 0, 0, 0.5f, 0, 0, 0);
            }

            return true;
        }

        return false;
    }

    public float getX(int pointIndex) {
        return this.shapePoints[pointIndex].getX();
    }

    public float getY(int pointIndex) {
        return this.shapePoints[pointIndex].getY();
    }

    public void setXY(int pointIndex, float x, float y) {
        Point point = this.shapePoints[pointIndex];

        point.setX(x);
        point.setY(y);
    }

    public float getU(int pointIndex) {
        return this.sheetPoints[pointIndex].getX() / 65535f;
    }

    public float getV(int pointIndex) {
        return this.sheetPoints[pointIndex].getY() / 65535f;
    }

    public void setUV(int pointIndex, float u, float v) {
        Point point = this.sheetPoints[pointIndex];

        point.setX(u * 65535f);
        point.setY(v * 65535f);
    }

    public SWFTexture getTexture() {
        return (SWFTexture) image;
    }

    public Tag getTag() {
        return tag;
    }

    public void setTag(Tag tag) {
        this.tag = tag;
    }

    public int getVertexCount() {
        return vertexCount;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;

        if (other instanceof ShapeDrawBitmapCommand command) {

            return Arrays.equals(command.shapePoints, this.shapePoints) &&
                Arrays.equals(command.sheetPoints, this.sheetPoints);
        }

        return false;
    }

    private static int[] getIndices(int trianglesCount) {
        int[] indices = new int[trianglesCount * 3];
        for (int i = 0; i < trianglesCount; i++) {
            indices[i * 3] = 0;
            indices[i * 3 + 1] = i + 1;
            indices[i * 3 + 2] = i + 2;
        }
        return indices;
    }
}
