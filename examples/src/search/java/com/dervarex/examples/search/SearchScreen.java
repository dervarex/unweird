package com.dervarex.examples.search;

import com.dervarex.unweird.Each;
import com.dervarex.unweird.Field;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.State;
import com.dervarex.unweird.annotation.Title;

import java.util.List;

@Title("Search")
public class SearchScreen extends Screen {

    @State List<Book> results = List.of();
    @State String status = "";
    private int latest; // answers to older queries are dropped

    Text heading = new Text("Books");
    Field query  = new Field("Search by title or author").onChange(q -> search());
    Text info    = new Text(() -> status).muted();
    Each<Book> list = new Each<>(() -> results, BookCard::new);

    {
        search();
    }

    private void search() {
        int mine = ++latest;
        status = "Searching…";
        async(() -> Library.search(query.value()))      // background thread
                .then(found -> {                          // back on the UI thread
                    if (mine != latest) {
                        return;
                    }
                    results = found;
                    status = found.isEmpty() ? "Nothing found." : found.size() + " books";
                });
    }
}
