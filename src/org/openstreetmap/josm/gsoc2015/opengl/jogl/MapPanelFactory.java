package org.openstreetmap.josm.gsoc2015.opengl.jogl;

import java.awt.Component;
import java.util.List;

import javax.swing.JComponent;

import org.openstreetmap.josm.gui.MapView;

/**
 * Removes all class dependencies to JOGL. This is required to allow referencing
 * the map panel without requiring any jogl dependencies. This class can thus
 * be used before JOGL is loaded.
 *
 * @author Michael Zangl
 *
 */
public class MapPanelFactory {
	public static Component getMapRenderer(MapView mapView, List<? extends JComponent> navigationComponents) {
		return new MapPanel(mapView, navigationComponents);
	}
}
