package com.dervarex.examples.navigation;

import com.dervarex.unweird.ListView;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Title;

@Title("Archive")
public class ArchiveScreen extends Screen {

    Text heading = new Text("Archive");
    ListView<Mail> list = new ListView<>(() -> Mail.archive).empty("Nothing archived yet.");
}
