package com.vorono4ka.editor.layout.menubar.menus;

import com.vorono4ka.editor.Editor;
import com.vorono4ka.editor.Main;
import com.vorono4ka.editor.layout.Window;
import com.vorono4ka.swf.SupercellSWF;
import com.vorono4ka.swf.exceptions.UnableToFindObjectException;
import com.vorono4ka.swf.originalObjects.MovieClipOriginal;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class EditMenu extends JMenu {
    private final JMenuItem previous;
    private final JMenuItem next;

    public EditMenu() {
        super("Edit");

        setMnemonic(KeyEvent.VK_E);

        JMenuItem find = new JMenuItem("Find", KeyEvent.VK_F);
        find.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));

        JMenuItem findUsages = new JMenuItem("Find Usages");
        find.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
        this.previous = new JMenuItem("Previous", KeyEvent.VK_P);
        this.previous.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK));
        this.next = new JMenuItem("Next", KeyEvent.VK_N);
        this.next.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK));

        find.addActionListener(this::find);
        findUsages.addActionListener(this::findUsages);
        this.previous.addActionListener(this::previous);
        this.next.addActionListener(this::next);

        this.previous.setEnabled(false);
        this.next.setEnabled(false);

        this.add(find);
        this.add(findUsages);
        this.addSeparator();
        this.add(this.previous);
        this.add(this.next);
    }

    private void find(ActionEvent actionEvent) {
        Window window = Main.editor.getWindow();
        window.getTabbedPane().setSelectedIndex(0);
        window.getDisplayObjectPanel().setFocusOnTextField();
    }

    private void findUsages(ActionEvent event) {
        SupercellSWF swf = Main.editor.getSwf();
        int[] ids = swf.getMovieClipsIds();
        try {
            for (int i = 0; i < swf.getMovieClipsCount(); i++) {
                MovieClipOriginal movieClipOriginal = swf.getOriginalMovieClip(ids[i], null);
                // TODO
            }
        } catch (UnableToFindObjectException e) {
            throw new RuntimeException(e);
        }
    }

    private void previous(ActionEvent e) {
        Main.editor.selectPrevious();
    }

    private void next(ActionEvent e) {
        Main.editor.selectNext();
    }

    public void checkPreviousAvailable() {
        this.previous.setEnabled(Main.editor.getSelectedIndex() > 0);
    }

    public void checkNextAvailable() {
        Editor editor = Main.editor;

        this.next.setEnabled(editor.getSelectedIndex() + 1 < editor.getClonedObjectCount());
    }
}
