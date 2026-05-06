package com.familytree.ui;

import com.familytree.model.FamilyTree;
import com.familytree.store.TreeStore;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;

public final class MainView extends BorderPane {
    public MainView(FamilyTree tree, TreeStore store) {
        SelectionModel selection = new SelectionModel();

        SearchPane search = new SearchPane(tree, selection);
        TreeCanvas canvas = new TreeCanvas(tree, selection, store);
        PersonCardPane card = new PersonCardPane(tree, selection, store, canvas::refresh);
        ScrollPane cardScroll = new ScrollPane(card);
        cardScroll.setFitToWidth(true);
        cardScroll.setPannable(true);

        SplitPane split = new SplitPane();
        split.getItems().addAll(search, canvas, cardScroll);
        split.setDividerPositions(0.20, 0.70);

        setPadding(new Insets(10));
        setCenter(split);
        setTop(new TopBar(tree, store, selection, canvas));
    }
}

