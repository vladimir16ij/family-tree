package com.familytree.ui;

import com.familytree.model.FamilyTree;
import com.familytree.model.MediaRef;
import com.familytree.model.Person;
import com.familytree.model.RelationshipType;
import com.familytree.store.TreeStore;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class PersonCardPane extends VBox {
    private final FamilyTree tree;
    private final SelectionModel selection;
    private final TreeStore store;
    private final Runnable onTreeChanged;

    private final Label title = new Label("Person");
    private final TextField firstName = new TextField();
    private final TextField lastName = new TextField();
    private final DatePicker birth = new DatePicker();
    private final DatePicker death = new DatePicker();
    private final TextArea notes = new TextArea();
    private final ListView<MediaRef> mediaList = new ListView<>();

    private final ListView<Person> partners = new ListView<>();
    private final ListView<Person> parents = new ListView<>();
    private final ListView<Person> children = new ListView<>();
    private final ComboBox<Person> addPartnerPick = new ComboBox<>();
    private final ComboBox<Person> addParentPick = new ComboBox<>();
    private final ComboBox<Person> addChildPick = new ComboBox<>();

    private Person current;
    private boolean mutatingUi;

    public PersonCardPane(FamilyTree tree, SelectionModel selection, TreeStore store, Runnable onTreeChanged) {
        this.tree = tree;
        this.selection = selection;
        this.store = store;
        this.onTreeChanged = onTreeChanged == null ? () -> {} : onTreeChanged;

        setSpacing(8);
        setPadding(new Insets(10));

        title.setStyle("-fx-font-size: 14px; -fx-font-weight: 800;");

        firstName.setPromptText("First name");
        lastName.setPromptText("Last name");
        notes.setPromptText("Notes");
        notes.setWrapText(true);
        notes.setPrefRowCount(6);

        mediaList.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(MediaRef item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.fileName());
            }
        });

        partners.setCellFactory(v -> new PersonCell());
        parents.setCellFactory(v -> new PersonCell());
        children.setCellFactory(v -> new PersonCell());
        partners.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        parents.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        children.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        addPartnerPick.setPromptText("Pick person...");
        addParentPick.setPromptText("Pick person...");
        addChildPick.setPromptText("Pick person...");
        addPartnerPick.setCellFactory(v -> new PersonCell());
        addParentPick.setCellFactory(v -> new PersonCell());
        addChildPick.setCellFactory(v -> new PersonCell());
        addPartnerPick.setButtonCell(new PersonCell());
        addParentPick.setButtonCell(new PersonCell());
        addChildPick.setButtonCell(new PersonCell());

        Button addMedia = new Button("Add media...");
        addMedia.setOnAction(evt -> onAddMedia());

        Button removeMedia = new Button("Remove selected media");
        removeMedia.setOnAction(evt -> onRemoveMedia());

        Button addPartnerBtn = new Button("Add partner");
        addPartnerBtn.setOnAction(evt -> {
            if (current == null) return;
            Person other = addPartnerPick.getValue();
            if (other == null) return;
            tree.addRelationship(new com.familytree.model.Relationship(current.id(), other.id(), RelationshipType.PARTNER_OF));
            store.save(tree);
            refreshRelationships();
            this.onTreeChanged.run();
        });

        Button removePartnerBtn = new Button("Remove selected partner");
        removePartnerBtn.setOnAction(evt -> {
            if (current == null) return;
            Person other = partners.getSelectionModel().getSelectedItem();
            if (other == null) return;
            tree.removeAllRelationshipsBetween(current.id(), other.id(), RelationshipType.PARTNER_OF);
            store.save(tree);
            refreshRelationships();
            this.onTreeChanged.run();
        });

        Button addParentBtn = new Button("Add parent");
        addParentBtn.setOnAction(evt -> {
            if (current == null) return;
            Person parent = addParentPick.getValue();
            if (parent == null) return;
            tree.addRelationship(new com.familytree.model.Relationship(parent.id(), current.id(), RelationshipType.PARENT_OF));
            store.save(tree);
            refreshRelationships();
            this.onTreeChanged.run();
        });

        Button removeParentBtn = new Button("Remove selected parent");
        removeParentBtn.setOnAction(evt -> {
            if (current == null) return;
            Person parent = parents.getSelectionModel().getSelectedItem();
            if (parent == null) return;
            tree.removeAllRelationshipsBetween(parent.id(), current.id(), RelationshipType.PARENT_OF);
            store.save(tree);
            refreshRelationships();
            this.onTreeChanged.run();
        });

        Button addChildBtn = new Button("Add child");
        addChildBtn.setOnAction(evt -> {
            if (current == null) return;
            Person child = addChildPick.getValue();
            if (child == null) return;
            tree.addRelationship(new com.familytree.model.Relationship(current.id(), child.id(), RelationshipType.PARENT_OF));
            store.save(tree);
            refreshRelationships();
            this.onTreeChanged.run();
        });

        Button removeChildBtn = new Button("Remove selected child");
        removeChildBtn.setOnAction(evt -> {
            if (current == null) return;
            Person child = children.getSelectionModel().getSelectedItem();
            if (child == null) return;
            tree.removeAllRelationshipsBetween(current.id(), child.id(), RelationshipType.PARENT_OF);
            store.save(tree);
            refreshRelationships();
            this.onTreeChanged.run();
        });

        selection.selectedPersonProperty().addListener((obs, oldV, newV) -> setPerson(newV));

        Runnable saveFromUi = () -> {
            if (mutatingUi || current == null) return;
            current.setFirstName(firstName.getText());
            current.setLastName(lastName.getText());
            current.setBirthDate(birth.getValue());
            current.setDeathDate(death.getValue());
            current.setNotes(notes.getText());
            title.setText(current.displayName());
            store.save(tree);
        };

        firstName.textProperty().addListener((o, a, b) -> saveFromUi.run());
        lastName.textProperty().addListener((o, a, b) -> saveFromUi.run());
        birth.valueProperty().addListener((o, a, b) -> saveFromUi.run());
        death.valueProperty().addListener((o, a, b) -> saveFromUi.run());
        notes.textProperty().addListener((o, a, b) -> saveFromUi.run());

        getChildren().addAll(
                title,
                new Label("First name"), firstName,
                new Label("Last name"), lastName,
                new Label("Birth date"), birth,
                new Label("Death date"), death,
                new Label("Notes"), notes,
                new Label("Relationships"),
                new Label("Partners"), partners,
                addPartnerPick, addPartnerBtn, removePartnerBtn,
                new Label("Parents"), parents,
                addParentPick, addParentBtn, removeParentBtn,
                new Label("Children"), children,
                addChildPick, addChildBtn, removeChildBtn,
                new Label("Media"), mediaList,
                addMedia, removeMedia
        );
        VBox.setVgrow(partners, Priority.NEVER);
        partners.setPrefHeight(80);
        VBox.setVgrow(parents, Priority.NEVER);
        parents.setPrefHeight(80);
        VBox.setVgrow(children, Priority.NEVER);
        children.setPrefHeight(100);
        VBox.setVgrow(mediaList, Priority.ALWAYS);

        setPerson(null);
    }

    private void setPerson(Person p) {
        mutatingUi = true;
        try {
            current = p;
            boolean has = p != null;
            setDisable(!has);
            if (!has) {
                title.setText("Person");
                firstName.setText("");
                lastName.setText("");
                birth.setValue(null);
                death.setValue(null);
                notes.setText("");
                mediaList.getItems().clear();
                partners.getItems().clear();
                parents.getItems().clear();
                children.getItems().clear();
                return;
            }

            title.setText(p.displayName());
            firstName.setText(p.firstName());
            lastName.setText(p.lastName());
            birth.setValue(p.birthDate());
            death.setValue(p.deathDate());
            notes.setText(p.notes() == null ? "" : p.notes());
            mediaList.getItems().setAll(p.media());
            refreshRelationshipPickers();
            refreshRelationships();
        } finally {
            mutatingUi = false;
        }
    }

    private void refreshRelationshipPickers() {
        if (current == null) return;
        var options = tree.people().stream()
                .filter(p -> !p.id().equals(current.id()))
                .toList();
        addPartnerPick.getItems().setAll(options);
        addParentPick.getItems().setAll(options);
        addChildPick.getItems().setAll(options);
    }

    private void refreshRelationships() {
        if (current == null) return;
        partners.getItems().setAll(tree.partnersOf(current.id()));
        parents.getItems().setAll(tree.parentsOf(current.id()));
        children.getItems().setAll(tree.childrenOf(current.id()));
    }

    private void onAddMedia() {
        if (current == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose media file");
        File f = fc.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (f == null) return;

        try {
            Files.createDirectories(store.mediaDir());
            String id = UUID.randomUUID().toString();
            String safeName = f.getName().replaceAll("[^a-zA-Z0-9._-]+", "_");
            String storedName = id + "_" + safeName;
            Path dest = store.mediaDir().resolve(storedName);
            Files.copy(f.toPath(), dest);

            String mime = Files.probeContentType(dest);
            if (mime == null) mime = "application/octet-stream";

            MediaRef ref = new MediaRef(id, f.getName(), "media/" + storedName, mime);
            current.media().add(ref);
            mediaList.getItems().setAll(current.media());
            store.save(tree);
        } catch (IOException e) {
            throw new RuntimeException("Failed to add media", e);
        }
    }

    private void onRemoveMedia() {
        if (current == null) return;
        MediaRef selected = mediaList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        current.media().removeIf(m -> m.id().equals(selected.id()));
        mediaList.getItems().setAll(current.media());
        store.save(tree);
    }

    private static final class PersonCell extends ListCell<Person> {
        @Override
        protected void updateItem(Person item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.displayName());
        }
    }
}

