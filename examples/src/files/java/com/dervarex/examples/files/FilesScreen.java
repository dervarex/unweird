package com.dervarex.examples.files;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Each;
import com.dervarex.unweird.Pointer;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.State;
import com.dervarex.unweird.annotation.Title;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Title("Files")
public class FilesScreen extends Screen {

    @State final List<Path> files = new ArrayList<>();

    Text heading  = new Text("Drop files here");
    Text subtitle = new Text(() -> files.isEmpty()
            ? "Drag files from your file manager onto this window."
            : files.size() + (files.size() == 1 ? " file" : " files"));
    Each<Path> rows = new Each<>(() -> files, FileRow::new);
    Button clear  = new Button("Clear").secondary().visibleIf(() -> !files.isEmpty()).onClick(files::clear);

    @Override
    protected boolean onDrop(Pointer at, List<Path> dropped) {
        dropped.stream().filter(p -> !files.contains(p)).forEach(files::add);
        return true; // taken
    }
}
