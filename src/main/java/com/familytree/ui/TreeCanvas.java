package com.familytree.ui;

import com.familytree.model.FamilyTree;
import com.familytree.model.Person;
import com.familytree.model.Relationship;
import com.familytree.model.RelationshipType;
import com.familytree.store.TreeStore;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class TreeCanvas extends Pane {
    private final FamilyTree tree;
    private final SelectionModel selection;
    private final TreeStore store;

    private final Map<String, NodeView> nodeViews = new HashMap<>();
    private Person pendingFrom;
    private RelationshipType pendingType;

    public TreeCanvas(FamilyTree tree, SelectionModel selection, TreeStore store) {
        this.tree = tree;
        this.selection = selection;
        this.store = store;

        setPadding(new Insets(10));
        setStyle("-fx-background-color: #0f172a; -fx-background-insets: 0; -fx-background-radius: 12;");

        selection.selectedPersonProperty().addListener((obs, oldV, newV) -> {
            updateSelectionStyles();
            updateRelationshipHighlighting();
        });

        refresh();
    }

    public void refresh() {
        getChildren().clear();
        nodeViews.clear();

        for (Person p : tree.people()) {
            NodeView v = new NodeView(p);
            nodeViews.put(p.id().value(), v);
        }

        // draw relationship lines first (under nodes), now that nodes exist
        for (Relationship r : tree.relationships()) {
            NodeView from = nodeViews.get(r.from().value());
            NodeView to = nodeViews.get(r.to().value());
            if (from == null || to == null) continue;

            Line line = new Line();
            styleLine(line, r, false);
            line.startXProperty().bind(from.layoutXProperty().add(70));
            line.startYProperty().bind(from.layoutYProperty().add(22));
            line.endXProperty().bind(to.layoutXProperty().add(70));
            line.endYProperty().bind(to.layoutYProperty().add(22));
            getChildren().add(line);

            // label at midpoint (kept subtle; becomes brighter when highlighted)
            Text tag = new Text(r.type() == RelationshipType.PARTNER_OF ? "Partner" : "Parent");
            tag.setFont(Font.font(11));
            tag.setFill(Color.web("#cbd5e1"));
            tag.setOpacity(0.35);
            tag.xProperty().bind(line.startXProperty().add(line.endXProperty()).divide(2).subtract(22));
            tag.yProperty().bind(line.startYProperty().add(line.endYProperty()).divide(2).subtract(6));
            getChildren().add(tag);

            // store references on nodes for updateRelationshipHighlighting
            from.lines.add(new RelLine(r, line, tag));
            to.lines.add(new RelLine(r, line, tag));
        }

        // nodes on top
        getChildren().addAll(nodeViews.values());

        updateSelectionStyles();
        updateRelationshipHighlighting();
    }

    private void updateSelectionStyles() {
        Person sel = selection.getSelectedPerson();
        for (NodeView v : nodeViews.values()) {
            v.setSelected(sel != null && v.person != null && v.person.id().equals(sel.id()));
        }
    }

    private void updateRelationshipHighlighting() {
        Person sel = selection.getSelectedPerson();
        Set<String> highlight = new HashSet<>();
        if (sel != null) {
            highlight.add(sel.id().value());
            tree.partnersOf(sel.id()).forEach(p -> highlight.add(p.id().value()));
            tree.parentsOf(sel.id()).forEach(p -> highlight.add(p.id().value()));
            tree.childrenOf(sel.id()).forEach(p -> highlight.add(p.id().value()));
        }

        for (NodeView v : nodeViews.values()) {
            for (RelLine rl : v.lines) {
                boolean active = sel != null
                        && (rl.r.from().equals(sel.id()) || rl.r.to().equals(sel.id()))
                        && highlight.contains(rl.r.from().value())
                        && highlight.contains(rl.r.to().value());
                styleLine(rl.line, rl.r, active);
                rl.tag.setOpacity(active ? 0.95 : 0.35);
            }
        }
    }

    private static void styleLine(Line line, Relationship r, boolean active) {
        if (r.type() == RelationshipType.PARTNER_OF) {
            line.setStroke(Color.web(active ? "#93c5fd" : "#60a5fa"));
            line.setStrokeWidth(active ? 4.0 : 2.5);
        } else {
            line.setStroke(Color.web(active ? "#fbbf24" : "#94a3b8"));
            line.setStrokeWidth(active ? 3.5 : 2.0);
        }
        line.setOpacity(active ? 1.0 : 0.55);
    }

    private final class NodeView extends Pane {
        private final Person person;
        private final Rectangle bg = new Rectangle(140, 44);
        private final Text label = new Text();
        private final Set<RelLine> lines = new HashSet<>();

        private double dragOffsetX;
        private double dragOffsetY;

        NodeView(Person person) {
            this.person = person;
            setPickOnBounds(false);

            bg.setArcWidth(12);
            bg.setArcHeight(12);
            bg.setFill(Color.web("#111827"));
            bg.setStroke(Color.web("#334155"));
            bg.setStrokeWidth(1.2);

            label.setFill(Color.web("#e2e8f0"));
            label.setStyle("-fx-font-size: 12px; -fx-font-weight: 700;");
            label.setX(10);
            label.setY(26);

            getChildren().addAll(bg, label);

            if (person != null) {
                relocate(person.x(), person.y());
                label.setText(person.displayName());
            } else {
                // placeholder view to satisfy bindings; not shown.
                setVisible(false);
            }

            setOnMousePressed(e -> {
                if (person == null) return;
                if (e.getButton() != MouseButton.PRIMARY) return;
                selection.setSelectedPerson(person);
                dragOffsetX = e.getX();
                dragOffsetY = e.getY();
                setCursor(Cursor.MOVE);
            });

            setOnMouseDragged(e -> {
                if (person == null) return;
                if (e.getButton() != MouseButton.PRIMARY) return;
                double nx = getLayoutX() + (e.getX() - dragOffsetX);
                double ny = getLayoutY() + (e.getY() - dragOffsetY);
                relocate(nx, ny);
                person.setX(nx);
                person.setY(ny);
            });

            setOnMouseReleased(e -> {
                if (person == null) return;
                setCursor(Cursor.HAND);
                store.save(tree);
            });

            setCursor(Cursor.HAND);

            setOnMouseClicked(e -> {
                if (person == null) return;
                if (e.getButton() == MouseButton.PRIMARY) {
                    // Relationship creation:
                    // - Shift+Click: partner link
                    // - Alt+Click: parent-of link (from -> to)
                    if (e.isShiftDown() || e.isAltDown()) {
                        RelationshipType t = e.isShiftDown() ? RelationshipType.PARTNER_OF : RelationshipType.PARENT_OF;
                        if (pendingFrom == null) {
                            pendingFrom = person;
                            pendingType = t;
                            bg.setStroke(Color.web("#f59e0b"));
                            bg.setStrokeWidth(2.2);
                        } else {
                            if (!pendingFrom.id().equals(person.id()) && pendingType == t) {
                                tree.addRelationship(new Relationship(pendingFrom.id(), person.id(), t));
                                store.save(tree);
                            }
                            pendingFrom = null;
                            pendingType = null;
                            refresh();
                        }
                        e.consume();
                        return;
                    }
                }
                if (e.getButton() == MouseButton.SECONDARY) {
                    ContextMenu menu = new ContextMenu();
                    MenuItem delete = new MenuItem("Delete person");
                    delete.setOnAction(evt -> {
                        tree.removePerson(person.id());
                        selection.setSelectedPerson(null);
                        store.save(tree);
                        refresh();
                    });
                    menu.getItems().add(delete);
                    menu.show(this, e.getScreenX(), e.getScreenY());
                }
            });
        }

        void setSelected(boolean selected) {
            if (selected) {
                bg.setStroke(Color.web("#22c55e"));
                bg.setStrokeWidth(2.2);
            } else {
                bg.setStroke(Color.web("#334155"));
                bg.setStrokeWidth(1.2);
            }
        }
    }

    private record RelLine(Relationship r, Line line, Text tag) {}
}

