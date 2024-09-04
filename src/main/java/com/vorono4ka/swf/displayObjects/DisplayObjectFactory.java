package com.vorono4ka.swf.displayObjects;

import com.vorono4ka.math.Rect;
import com.vorono4ka.swf.SupercellSWF;
import com.vorono4ka.swf.exceptions.UnableToFindObjectException;
import com.vorono4ka.swf.originalObjects.*;

public class DisplayObjectFactory {
    public static DisplayObject createFromOriginal(DisplayObjectOriginal original, SupercellSWF swf, Rect scalingGrid) throws UnableToFindObjectException {
        if (original instanceof MovieClipModifierOriginal movieClipModifierOriginal) {
            return MovieClipModifier.createModifier(movieClipModifierOriginal);
        }

        if (original instanceof ShapeOriginal shapeOriginal) {
            if (scalingGrid != null) {
                return Shape9Slice.createShape(shapeOriginal, scalingGrid);
            }

            return Shape.createShape(shapeOriginal);
        }

        if (original instanceof MovieClipOriginal movieClipOriginal) {
            return MovieClip.createMovieClip(movieClipOriginal, swf);
        }

        if (original instanceof TextFieldOriginal textFieldOriginal) {
            return TextField.createTextField(textFieldOriginal);
        }

        throw new IllegalStateException("Unexpected original object: " + original);
    }
}
