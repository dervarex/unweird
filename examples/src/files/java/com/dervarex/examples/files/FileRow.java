package com.dervarex.examples.files;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Component;
import com.dervarex.unweird.Image;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.annotation.Card;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Picks its widgets at runtime, so it lists them with add(...) instead of declaring fields. */
@Card
public class FileRow extends Component {

    FileRow(Path file) {
        Text name = new Text(file.getFileName().toString());
        Text details = new Text(describe(file)).muted();
        Button open = new Button("Open").ghost().onClick(() -> Unweird.open(file));
        if (isImage(file)) {
            add(Image.file(file).size(64, 64).radius(6), name, details, open);
        } else {
            add(name, details, open);
        }
    }

    private static boolean isImage(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif");
    }

    private static String describe(Path file) {
        try {
            if (Files.isDirectory(file)) {
                return "Folder";
            }
            long bytes = Files.size(file);
            return bytes < 1024 ? bytes + " B" : bytes < 1 << 20 ? bytes / 1024 + " KB" : bytes / (1 << 20) + " MB";
        } catch (IOException e) {
            return "Can't read it: " + e.getMessage();
        }
    }
}
