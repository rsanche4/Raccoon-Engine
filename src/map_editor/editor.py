import sys
import json
import os
from PyQt5.QtWidgets import (QApplication, QMainWindow, QGraphicsView, QGraphicsScene,
                            QDialog, QVBoxLayout, QHBoxLayout, QLabel, QLineEdit, 
                            QPushButton, QComboBox, QSpinBox, QDoubleSpinBox, 
                            QCheckBox, QMessageBox, QFormLayout, QScrollArea,
                            QWidget, QGroupBox, QGridLayout, QStatusBar)
from PyQt5.QtGui import QPainter, QPen, QMouseEvent, QColor, QBrush, QPolygonF, QFont
from PyQt5.QtCore import Qt, QPointF, pyqtSignal
import colorsys

GRID_SIZE = 32

class InitialDialog(QDialog):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Raccoon Engine Map Editor")
        self.setFixedSize(400, 200)
        self.choice = None
        
        layout = QVBoxLayout()
        
        title = QLabel("Would you like to load a map, or create a new map?")
        title.setAlignment(Qt.AlignCenter)
        layout.addWidget(title)
        
        button_layout = QHBoxLayout()
        
        load_btn = QPushButton("Load Map")
        load_btn.clicked.connect(lambda: self.set_choice("load"))
        
        create_btn = QPushButton("Create New Map")
        create_btn.clicked.connect(lambda: self.set_choice("create"))
        
        button_layout.addWidget(load_btn)
        button_layout.addWidget(create_btn)
        
        layout.addLayout(button_layout)
        self.setLayout(layout)
    
    def set_choice(self, choice):
        self.choice = choice
        self.accept()

class MapPropertiesDialog(QDialog):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("New Map Properties")
        self.setFixedSize(400, 300)
        
        layout = QFormLayout()
        
        self.map_name = QLineEdit("untitled_map")
        self.world_sector_height = QSpinBox()
        self.world_sector_height.setRange(64, 2048)
        self.world_sector_height.setValue(256)
        
        self.spawn_x = QDoubleSpinBox()
        self.spawn_x.setRange(-10000, 10000)
        self.spawn_x.setValue(0)
        
        self.spawn_z = QDoubleSpinBox()
        self.spawn_z.setRange(-10000, 10000)
        self.spawn_z.setValue(0)
        
        self.grid_size = QDoubleSpinBox()
        self.grid_size.setRange(0.1, 100)
        self.grid_size.setSingleStep(0.1)
        self.grid_size.setValue(1.0)
        
        self.skybox_texture = QLineEdit("sky01")
        
        self.map_size = QSpinBox()
        self.map_size.setRange(1000, 50000)
        self.map_size.setValue(5000)
        self.map_size.setSuffix(" units")
        
        layout.addRow("Map Name:", self.map_name)
        layout.addRow("World Sector Height Limit:", self.world_sector_height)
        layout.addRow("Player Spawn X:", self.spawn_x)
        layout.addRow("Player Spawn Z:", self.spawn_z)
        layout.addRow("Grid Cell Size:", self.grid_size)
        layout.addRow("Map Size (grid extent):", self.map_size)
        layout.addRow("Skybox Texture:", self.skybox_texture)
        
        button_layout = QHBoxLayout()
        ok_btn = QPushButton("OK")
        ok_btn.clicked.connect(self.accept)
        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(self.reject)
        
        button_layout.addWidget(ok_btn)
        button_layout.addWidget(cancel_btn)
        
        layout.addRow(button_layout)
        self.setLayout(layout)
    
    def get_properties(self):
        return {
            "map_name": self.map_name.text(),
            "world_sector_height": self.world_sector_height.value(),
            "spawn_x": self.spawn_x.value(),
            "spawn_z": self.spawn_z.value(),
            "grid_size": self.grid_size.value(),
            "map_size": self.map_size.value(),
            "skybox_texture": self.skybox_texture.text()
        }

class WallTextureWidget(QWidget):
    def __init__(self, wall_index):
        super().__init__()
        self.wall_index = wall_index
        
        layout = QHBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        
        self.texture_name = QLineEdit(f"wall{wall_index + 1}")
        self.texture_name.setPlaceholderText("Texture name")
        
        self.stretch_tile = QComboBox()
        self.stretch_tile.addItems(["Stretch", "Tile"])
        
        self.scale = QDoubleSpinBox()
        self.scale.setRange(0.1, 10.0)
        self.scale.setSingleStep(0.1)
        self.scale.setValue(1.0)
        self.scale.setEnabled(False)
        
        self.brightness = QDoubleSpinBox()
        self.brightness.setRange(0.0, 2.0)
        self.brightness.setSingleStep(0.1)
        self.brightness.setValue(1.0)
        
        self.stretch_tile.currentTextChanged.connect(self.on_stretch_tile_changed)
        
        layout.addWidget(QLabel(f"Wall {wall_index + 1}:"))
        layout.addWidget(self.texture_name)
        layout.addWidget(self.stretch_tile)
        layout.addWidget(QLabel("Scale:"))
        layout.addWidget(self.scale)
        layout.addWidget(QLabel("Brightness:"))
        layout.addWidget(self.brightness)
        
        self.setLayout(layout)
    
    def on_stretch_tile_changed(self, text):
        self.scale.setEnabled(text == "Tile")
    
    def get_data(self):
        return {
            "texture": self.texture_name.text(),
            "mode": self.stretch_tile.currentText().lower(),
            "scale": self.scale.value() if self.stretch_tile.currentText() == "Tile" else 1.0,
            "brightness": self.brightness.value()
        }

class SectorPropertiesDialog(QDialog):
    def __init__(self, num_walls):
        super().__init__()
        self.setWindowTitle("Sector Properties")
        self.setFixedSize(600, 500)
        self.num_walls = num_walls
        
        main_layout = QVBoxLayout()
        
        # Scroll area for the form
        scroll = QScrollArea()
        scroll_widget = QWidget()
        layout = QFormLayout()
        
        # Basic properties
        basic_group = QGroupBox("Basic Properties")
        basic_layout = QFormLayout()
        
        self.sector_id = QLineEdit("sector_1")
        self.floor_height = QSpinBox()
        self.floor_height.setRange(-1000, 1000)
        self.floor_height.setValue(0)
        
        self.ceiling_height = QSpinBox()
        self.ceiling_height.setRange(-1, 1000)
        self.ceiling_height.setValue(128)
        
        basic_layout.addRow("Sector ID:", self.sector_id)
        basic_layout.addRow("Floor Height:", self.floor_height)
        basic_layout.addRow("Ceiling Height (use -1 for no ceiling):", self.ceiling_height)
        basic_group.setLayout(basic_layout)
        
        # Floor texture
        floor_group = QGroupBox("Floor Texture")
        floor_layout = QFormLayout()
        
        self.floor_texture = QLineEdit("floor01")
        self.floor_stretch_tile = QComboBox()
        self.floor_stretch_tile.addItems(["Stretch", "Tile"])
        self.floor_scale = QDoubleSpinBox()
        self.floor_scale.setRange(0.1, 10.0)
        self.floor_scale.setValue(1.0)
        self.floor_scale.setEnabled(False)
        self.floor_brightness = QDoubleSpinBox()
        self.floor_brightness.setRange(0.0, 2.0)
        self.floor_brightness.setValue(1.0)
        
        self.floor_stretch_tile.currentTextChanged.connect(
            lambda text: self.floor_scale.setEnabled(text == "Tile")
        )
        
        floor_layout.addRow("Texture:", self.floor_texture)
        floor_layout.addRow("Mode:", self.floor_stretch_tile)
        floor_layout.addRow("Scale:", self.floor_scale)
        floor_layout.addRow("Brightness:", self.floor_brightness)
        floor_group.setLayout(floor_layout)
        
        # Ceiling texture
        ceiling_group = QGroupBox("Ceiling Texture")
        ceiling_layout = QFormLayout()
        
        self.ceiling_texture = QLineEdit("ceiling01")
        self.ceiling_stretch_tile = QComboBox()
        self.ceiling_stretch_tile.addItems(["Stretch", "Tile"])
        self.ceiling_scale = QDoubleSpinBox()
        self.ceiling_scale.setRange(0.1, 10.0)
        self.ceiling_scale.setValue(1.0)
        self.ceiling_scale.setEnabled(False)
        self.ceiling_brightness = QDoubleSpinBox()
        self.ceiling_brightness.setRange(0.0, 2.0)
        self.ceiling_brightness.setValue(1.0)
        
        self.ceiling_stretch_tile.currentTextChanged.connect(
            lambda text: self.ceiling_scale.setEnabled(text == "Tile")
        )
        
        ceiling_layout.addRow("Texture:", self.ceiling_texture)
        ceiling_layout.addRow("Mode:", self.ceiling_stretch_tile)
        ceiling_layout.addRow("Scale:", self.ceiling_scale)
        ceiling_layout.addRow("Brightness:", self.ceiling_brightness)
        ceiling_group.setLayout(ceiling_layout)
        
        # Wall textures (only if floor height > 0)
        self.wall_widgets = []
        self.ceiling_wall_widgets = []
        
        if num_walls > 0:
            walls_group = QGroupBox(f"Floor Wall Textures ({num_walls} walls)")
            walls_layout = QVBoxLayout()
            
            # Add note about floor height = 0
            note_label = QLabel("Note: Wall textures will be disabled if floor height is set to 0 (ground level)")
            note_label.setStyleSheet("color: #666; font-style: italic;")
            walls_layout.addWidget(note_label)
            
            for i in range(num_walls):
                wall_widget = WallTextureWidget(i)
                self.wall_widgets.append(wall_widget)
                walls_layout.addWidget(wall_widget)
            
            walls_group.setLayout(walls_layout)
            
            # Ceiling wall textures (only if ceiling height != -1)
            ceiling_walls_group = QGroupBox(f"Ceiling Wall Textures ({num_walls} walls)")
            ceiling_walls_layout = QVBoxLayout()
            
            # Add note about ceiling height = -1
            ceiling_note_label = QLabel("Note: Ceiling wall textures will be disabled if ceiling height is set to -1 (no ceiling)")
            ceiling_note_label.setStyleSheet("color: #666; font-style: italic;")
            ceiling_walls_layout.addWidget(ceiling_note_label)
            
            for i in range(num_walls):
                ceiling_wall_widget = WallTextureWidget(i)
                # Set default texture names to differentiate from floor walls
                ceiling_wall_widget.texture_name.setText(f"ceiling_wall{i + 1}")
                self.ceiling_wall_widgets.append(ceiling_wall_widget)
                ceiling_walls_layout.addWidget(ceiling_wall_widget)
            
            ceiling_walls_group.setLayout(ceiling_walls_layout)
            
            # Add all groups to main layout
            layout.addRow(basic_group)
            layout.addRow(floor_group)
            layout.addRow(ceiling_group)
            layout.addRow(walls_group)
            layout.addRow(ceiling_walls_group)
        else:
            # Add groups without walls
            layout.addRow(basic_group)
            layout.addRow(floor_group)
            layout.addRow(ceiling_group)
        
        scroll_widget.setLayout(layout)
        scroll.setWidget(scroll_widget)
        scroll.setWidgetResizable(True)
        
        main_layout.addWidget(scroll)
        
        # Buttons
        button_layout = QHBoxLayout()
        ok_btn = QPushButton("OK")
        ok_btn.clicked.connect(self.accept)
        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(self.reject)
        
        button_layout.addWidget(ok_btn)
        button_layout.addWidget(cancel_btn)
        main_layout.addLayout(button_layout)
        
        self.setLayout(main_layout)
        
        # Update ceiling texture availability based on ceiling height
        self.ceiling_height.valueChanged.connect(self.update_ceiling_texture_state)
        self.floor_height.valueChanged.connect(self.update_wall_texture_state)
        self.update_ceiling_texture_state()
        self.update_wall_texture_state()
    
    def update_ceiling_texture_state(self):
        has_ceiling = self.ceiling_height.value() != -1
        self.ceiling_texture.setEnabled(has_ceiling)
        self.ceiling_stretch_tile.setEnabled(has_ceiling)
        self.ceiling_scale.setEnabled(has_ceiling and self.ceiling_stretch_tile.currentText() == "Tile")
        self.ceiling_brightness.setEnabled(has_ceiling)
        
        # Update ceiling wall textures
        for ceiling_wall_widget in self.ceiling_wall_widgets:
            ceiling_wall_widget.setEnabled(has_ceiling)
    
    def update_wall_texture_state(self):
        has_walls = self.floor_height.value() > 0
        for wall_widget in self.wall_widgets:
            wall_widget.setEnabled(has_walls)
    
    def get_values(self):
        floor_data = {
            "texture": self.floor_texture.text(),
            "mode": self.floor_stretch_tile.currentText().lower(),
            "scale": self.floor_scale.value() if self.floor_stretch_tile.currentText() == "Tile" else 1.0,
            "brightness": self.floor_brightness.value()
        }
        
        ceiling_data = None
        if self.ceiling_height.value() != -1:
            ceiling_data = {
                "texture": self.ceiling_texture.text(),
                "mode": self.ceiling_stretch_tile.currentText().lower(),
                "scale": self.ceiling_scale.value() if self.ceiling_stretch_tile.currentText() == "Tile" else 1.0,
                "brightness": self.ceiling_brightness.value()
            }
        
        wall_data = []
        if self.floor_height.value() > 0:  # Only include wall data if floor height > 0
            wall_data = [widget.get_data() for widget in self.wall_widgets]
        
        ceiling_wall_data = []
        if self.ceiling_height.value() != -1:  # Only include ceiling wall data if ceiling exists
            ceiling_wall_data = [widget.get_data() for widget in self.ceiling_wall_widgets]
        
        return {
            "sector_id": self.sector_id.text(),
            "floor_height": self.floor_height.value(),
            "ceiling_height": self.ceiling_height.value() if self.ceiling_height.value() != -1 else None,
            "floor_texture": floor_data,
            "ceiling_texture": ceiling_data,
            "wall_textures": wall_data,
            "ceiling_wall_textures": ceiling_wall_data
        }

class EditorView(QGraphicsView):
    # Signal to emit coordinates to parent window
    coordinates_changed = pyqtSignal(float, float, float, float)
    
    def __init__(self, parent=None):
        super().__init__()
        self.parent_window = parent
        self.scene = QGraphicsScene(self)
        self.setScene(self.scene)
        self.setRenderHint(QPainter.Antialiasing)
        self.setDragMode(QGraphicsView.NoDrag)

        self.grid_size = GRID_SIZE
        self.zoom = 1.0
        self.pan_start = None

        self.current_polygon = []
        self.sectors = []

        self.setMouseTracking(True)
        self.draw_grid()

    def snap_to_grid(self, pos):
        return QPointF(
            round(pos.x() / self.grid_size) * self.grid_size,
            round(pos.y() / self.grid_size) * self.grid_size
        )

    def points_equal(self, a, b):
        return int(a.x()) == int(b.x()) and int(a.y()) == int(b.y())

    def edge_exists(self, p1, p2, points):
        return any(
            (self.points_equal(p1, points[i]) and self.points_equal(p2, points[i+1]))
            or
            (self.points_equal(p2, points[i]) and self.points_equal(p1, points[i+1]))
            for i in range(len(points) - 1)
        )

    def get_height_color(self, floor_height):
        """Generate a cool-warm color based on floor height"""
        if not hasattr(self.parent_window, 'map_properties'):
            return QColor(128, 128, 128)  # Default gray
        
        max_height = self.parent_window.map_properties.get('world_sector_height', 256)
        
        # Normalize height between 0 (sea level) and max_height
        normalized = max(0, min(1, floor_height / max_height))
        
        # Convert to HSV for cool-warm gradient
        # Blue (cool) = 240°, Red (warm) = 0°
        hue = (1 - normalized) * 240 / 360  # 240° to 0°
        saturation = 0.8
        value = 0.9
        
        rgb = colorsys.hsv_to_rgb(hue, saturation, value)
        return QColor(int(rgb[0] * 255), int(rgb[1] * 255), int(rgb[2] * 255))

    def keyPressEvent(self, event):
        if event.key() == Qt.Key_Delete:
            if self.current_polygon:
                self.current_polygon.pop()
            elif self.sectors:
                self.sectors.pop()
            self.draw_grid()
        elif event.key() == Qt.Key_Return or event.key() == Qt.Key_Enter:
            if self.parent_window:
                self.parent_window.save_map()

    def mousePressEvent(self, event):
        if event.button() == Qt.LeftButton:
            scene_pos = self.mapToScene(event.pos())
            snapped = self.snap_to_grid(scene_pos)

            # If first point is clicked again and we have enough vertices -> close polygon
            if len(self.current_polygon) >= 3 and self.points_equal(snapped, self.current_polygon[0]):
                num_walls = len(self.current_polygon)
                dialog = SectorPropertiesDialog(num_walls)
                if dialog.exec_():
                    props = dialog.get_values()
                    self.sectors.append({
                        "vertices": self.current_polygon[:],
                        "properties": props
                    })
                self.current_polygon.clear()
                self.draw_grid()
                return

            # Avoid duplicate vertices
            if any(self.points_equal(p, snapped) for p in self.current_polygon):
                return

            # Avoid duplicate lines
            if len(self.current_polygon) > 0:
                last = self.current_polygon[-1]
                if self.edge_exists(last, snapped, self.current_polygon):
                    return

                # Lock direction (orthogonal)
                dx = abs(snapped.x() - last.x())
                dy = abs(snapped.y() - last.y())
                if dx > dy:
                    snapped.setY(last.y())
                else:
                    snapped.setX(last.x())

            self.current_polygon.append(snapped)
            self.draw_grid()

        elif event.button() == Qt.RightButton:
            self.pan_start = event.pos()

    def mouseMoveEvent(self, event):
        # Update coordinates display
        scene_pos = self.mapToScene(event.pos())
        snapped = self.snap_to_grid(scene_pos)
        
        # Convert to grid coordinates
        grid_x = scene_pos.x() / self.grid_size
        grid_y = -scene_pos.y() / self.grid_size  # Negative Y for typical game coordinate system
        snapped_grid_x = snapped.x() / self.grid_size
        snapped_grid_y = -snapped.y() / self.grid_size
        
        # Emit coordinates to parent window
        self.coordinates_changed.emit(grid_x, grid_y, snapped_grid_x, snapped_grid_y)
        
        # Handle panning
        if self.pan_start:
            delta = self.pan_start - event.pos()
            self.pan_start = event.pos()
            self.horizontalScrollBar().setValue(self.horizontalScrollBar().value() + delta.x())
            self.verticalScrollBar().setValue(self.verticalScrollBar().value() + delta.y())

    def mouseReleaseEvent(self, event):
        if event.button() == Qt.RightButton:
            self.pan_start = None

    def wheelEvent(self, event):
        zoom_in_factor = 1.15
        zoom_out_factor = 1 / zoom_in_factor
        factor = zoom_in_factor if event.angleDelta().y() > 0 else zoom_out_factor
        self.zoom *= factor
        self.scale(factor, factor)

    def draw_grid(self):
        self.scene.clear()
        pen = QPen(QColor(200, 200, 200))
        pen.setWidth(0)

        # Use map size from properties
        map_size = 5000  # default
        if hasattr(self.parent_window, 'map_properties') and self.parent_window.map_properties:
            map_size = self.parent_window.map_properties.get('map_size', 5000)
        
        left, right = -map_size, map_size
        top, bottom = -map_size, map_size

        for x in range(left, right + 1, self.grid_size):
            self.scene.addLine(x, top, x, bottom, pen)
        for y in range(top, bottom + 1, self.grid_size):
            self.scene.addLine(left, y, right, y, pen)

        # Draw existing sectors with floor height color
        dot_pen = QPen(Qt.black)
        dot_pen.setWidth(2)
        
        for sector in self.sectors:
            verts = sector["vertices"]
            props = sector["properties"]
            
            # Create polygon for filling
            polygon = QPolygonF(verts)
            
            # Get color based on floor height
            floor_height = props.get("floor_height", 0)
            fill_color = self.get_height_color(floor_height)
            brush = QBrush(fill_color)
            
            # Draw filled polygon
            self.scene.addPolygon(polygon, dot_pen, brush)
            
            # Draw vertices
            for i, pt in enumerate(verts):
                self.scene.addEllipse(pt.x() - 3, pt.y() - 3, 6, 6, dot_pen, QBrush(Qt.white))

        # Draw current in-progress polygon
        if self.current_polygon:
            last = None
            for pt in self.current_polygon:
                self.scene.addEllipse(pt.x() - 3, pt.y() - 3, 6, 6, dot_pen, QBrush(Qt.red))
                if last:
                    line_pen = QPen(Qt.red)
                    line_pen.setWidth(2)
                    self.scene.addLine(last.x(), last.y(), pt.x(), pt.y(), line_pen)
                last = pt

class RaccoonEditor(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Raccoon Engine Sector Editor")
        self.setGeometry(100, 100, 800, 600)
        
        self.map_properties = None
        self.map_data = {"sectors": []}
        
        # Show initial dialog
        initial_dialog = InitialDialog()
        if initial_dialog.exec_():
            if initial_dialog.choice == "create":
                self.create_new_map()
            elif initial_dialog.choice == "load":
                QMessageBox.information(self, "Not Implemented", 
                                      "Load functionality is not yet implemented. Creating new map instead.")
                self.create_new_map()
        else:
            sys.exit()
        
        self.editor_view = EditorView(self)
        self.setCentralWidget(self.editor_view)
        
        # Connect coordinates signal
        self.editor_view.coordinates_changed.connect(self.update_coordinates_display)
        
        # Create status bar for coordinates display
        self.status_bar = QStatusBar()
        self.setStatusBar(self.status_bar)
        
        # Create coordinate labels
        self.coord_label = QLabel("Mouse: (0.0, 0.0) | Snapped: (0, 0)")
        self.coord_label.setStyleSheet("font-family: monospace; font-size: 12px; padding: 2px 5px;")
        self.status_bar.addPermanentWidget(self.coord_label)
        
        # Add some helpful text
        self.status_bar.showMessage("Left click to place vertices, right click and drag to pan, mouse wheel to zoom")
        
        # Update window title with map name
        if self.map_properties:
            self.setWindowTitle(f"Raccoon Engine Sector Editor - {self.map_properties['map_name']}")

    def update_coordinates_display(self, mouse_x, mouse_y, snapped_x, snapped_y):
        """Update the coordinates display in the status bar"""
        coord_text = f"Mouse: ({mouse_x:.1f}, {mouse_y:.1f}) | Snapped: ({snapped_x:.0f}, {snapped_y:.0f})"
        self.coord_label.setText(coord_text)

    def create_new_map(self):
        """Create a new map with user-defined properties"""
        dialog = MapPropertiesDialog()
        if dialog.exec_():
            self.map_properties = dialog.get_properties()
            self.map_data = {
                "map_properties": self.map_properties,
                "sectors": []
            }
        else:
            sys.exit()

    def save_map(self):
        """Save the current map to a JSON file"""
        if not self.map_properties:
            QMessageBox.warning(self, "Error", "No map properties defined!")
            return
        
        # Update map data with current sectors
        self.map_data["sectors"] = []
        for sector in self.editor_view.sectors:
            sector_data = {
                "vertices": [[pt.x(), pt.y()] for pt in sector["vertices"]],
                "properties": sector["properties"]
            }
            self.map_data["sectors"].append(sector_data)
        
        # Save to file
        filename = f"{self.map_properties['map_name']}.json"
        try:
            with open(filename, 'w') as f:
                json.dump(self.map_data, f, indent=2)
            QMessageBox.information(self, "Success", f"Map saved as {filename}")
        except Exception as e:
            QMessageBox.critical(self, "Error", f"Failed to save map: {str(e)}")

if __name__ == "__main__":
    app = QApplication(sys.argv)
    window = RaccoonEditor()
    window.show()
    sys.exit(app.exec_())