package com.dervarex.examples.search;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Component;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.annotation.Card;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** @Card draws the component on its own padded surface. */
@Card
public class BookCard extends Component {

    final Text title;
    final Text meta;
    final Button open;

    BookCard(Book book) {
        title = new Text(book.title());
        meta = new Text(book.author() + "  ·  " + book.year()).muted();
        open = new Button("Look it up").ghost().onClick(() ->
                Unweird.open("https://duckduckgo.com/?q=" + URLEncoder.encode(book.title(), StandardCharsets.UTF_8)));
    }
}
