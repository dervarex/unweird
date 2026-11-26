package com.dervarex.examples.navigation;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Component;
import com.dervarex.unweird.Side;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Default;
import com.dervarex.unweird.annotation.Shortcut;

/** Docked left on every screen. In a sidebar, buttons look like navigation items. */
@Default(Side.LEFT)
public class Sidebar extends Component {

    Text brand      = new Text("Mail");
    Button inbox    = new Button("Inbox").onClick(this::inbox);
    Button archive  = new Button("Archive").onClick(this::archive);
    Button settings = new Button("Settings").onClick(this::settings);

    @Shortcut("Ctrl+1") void inbox()    { go(InboxScreen.class); }
    @Shortcut("Ctrl+2") void archive()  { go(ArchiveScreen.class); }
    @Shortcut("Ctrl+3") void settings() { go(SettingsScreen.class); }
}
