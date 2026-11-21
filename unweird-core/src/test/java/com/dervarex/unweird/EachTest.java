package com.dervarex.unweird;

import com.dervarex.unweird.annotation.Card;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EachTest {

    record Item(String id) {
    }

    @Card
    static class Row extends Component {
        final Item item;
        Text name;
        int clicks;
        Button click = new Button("+").onClick(() -> clicks++);

        Row(Item item) {
            this.item = item;
            this.name = new Text(item.id());
        }
    }

    static class Results extends Screen {
        final List<Item> items = new ArrayList<>(List.of(new Item("a"), new Item("b")));
        Each<Item> rows = new Each<>(() -> items, Row::new);
    }

    @Test
    void rowsFollowTheListAndKeepTheirState() {
        Results s = new Results();
        s.update();
        Row a = (Row) s.rows.viewOf(new Item("a"));
        a.click.click();
        assertEquals(2, s.rows.children().size());

        s.items.addFirst(new Item("c"));
        s.items.remove(new Item("b"));
        s.update();

        assertEquals(List.of(new Item("c"), new Item("a")), s.rows.items());
        assertSame(a, s.rows.children().get(1));
        assertEquals(1, a.clicks);
        assertNull(s.rows.viewOf(new Item("b")));
    }

    @Test
    void removedRowsAreReleasedAndCanBeShownElsewhere() {
        Results s = new Results();
        s.update();
        Widget b = s.rows.viewOf(new Item("b"));
        s.items.remove(new Item("b"));
        s.update();
        assertNull(b.parent);
    }

    @Test
    void duplicateItemsAreReported() {
        Results s = new Results();
        s.items.add(new Item("a"));
        var error = assertThrows(IllegalStateException.class, s::update);
        assertTrue(error.getMessage().contains("twice"), error.getMessage());
    }

    @Test
    void cardsAreMarkedAndTheirButtonsAreFocusable() {
        Results s = new Results();
        s.update();
        Row a = (Row) s.rows.viewOf(new Item("a"));
        assertTrue(a.card);
        assertFalse(s.rows.card);
        assertEquals(2, s.focusables().size());
    }
}
