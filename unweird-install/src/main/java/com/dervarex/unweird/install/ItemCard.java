package com.dervarex.unweird.install;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Component;
import com.dervarex.unweird.Image;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.Toast;
import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.annotation.Card;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * One item in a {@link ContentBrowser}: icon, title, description, a status line and Install / Remove.
 * Everything that depends on the install state reads {@link Installer#status} live, so it can't go stale.
 */
@Card
public class ItemCard extends Component {

    @Getter private final Item item;
    @Getter private final Installer installer;

    @Getter private final Image icon;
    @Getter private final Details details;
    @Getter private final Button install;
    @Getter private final Button remove;

    public ItemCard(Item item, Installer installer) {
        this.item = item;
        this.installer = installer;
        this.icon = item.iconUrl() == null ? null : Image.url(item.iconUrl()).size(48, 48).radius(10);
        this.details = new Details(this);
        if (icon != null) {
            details.rightOf(icon);
        }
        this.install = new Button(() -> status().label())
                .rightOf(details)
                .visibleIf(() -> !status().installed())
                .enabledIf(() -> !status().busy())
                .onClick(this::install);
        this.remove = new Button("Remove").secondary()
                .rightOf(details)
                .visibleIf(() -> status().installed())
                .onClick(() -> installer.uninstall(item));
    }

    private void install() {
        installer.install(item).whenComplete((done, error) -> Unweird.toast(error == null
                ? Toast.success("Installed " + item.title())
                : Toast.error("Couldn't install " + item.title() + ": " + installer.status(item).message())));
    }

    public Installer.Status status() {
        return installer.status(item);
    }

    /** "by Author · 1.2M downloads · Installed", or the reason it failed. */
    String meta() {
        Installer.Status status = status();
        if (status.state() == Installer.State.FAILED) {
            return "Couldn't install: " + status.message();
        }
        List<String> parts = new ArrayList<>();
        if (item.author() != null && !item.author().isBlank()) {
            parts.add("by " + item.author());
        }
        if (item.detail() != null && !item.detail().isBlank()) {
            parts.add(item.detail());
        }
        if (status.installed()) {
            parts.add("Installed");
        }
        return String.join("  ·  ", parts);
    }

    /** The text column of a card. */
    public static class Details extends Component {

        @Getter private final Text title;
        @Getter private final Text description;
        @Getter private final Text meta;

        Details(ItemCard card) {
            Item item = card.item;
            this.title = new Text(item.title());
            this.description = new Text(item.description() == null ? "" : item.description().strip()).maxLines(2);
            this.meta = new Text(card::meta).muted();
        }
    }
}
