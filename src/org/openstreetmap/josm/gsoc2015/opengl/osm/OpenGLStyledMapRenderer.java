package org.openstreetmap.josm.gsoc2015.opengl.osm;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.media.opengl.GL2;

import org.jogamp.glg2d.GLGraphics2D;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.visitor.paint.StyledMapRenderer;
import org.openstreetmap.josm.gsoc2015.opengl.geometrycache.DrawStats;
import org.openstreetmap.josm.gsoc2015.opengl.geometrycache.GLState;
import org.openstreetmap.josm.gsoc2015.opengl.geometrycache.GeometryMerger;
import org.openstreetmap.josm.gsoc2015.opengl.geometrycache.RecordedOsmGeometries;
import org.openstreetmap.josm.gsoc2015.opengl.osm.StyledGeometryGenerator.ChacheDataSupplier;
import org.openstreetmap.josm.gsoc2015.opengl.osm.search.NodeSearcher;
import org.openstreetmap.josm.gsoc2015.opengl.osm.search.OsmPrimitiveHandler;
import org.openstreetmap.josm.gsoc2015.opengl.osm.search.RelationSearcher;
import org.openstreetmap.josm.gsoc2015.opengl.osm.search.WaySearcher;
import org.openstreetmap.josm.gui.NavigatableComponent;

import sun.awt.RepaintArea;

public class OpenGLStyledMapRenderer extends StyledMapRenderer {

	public OpenGLStyledMapRenderer(GLGraphics2D g, NavigatableComponent nc,
			boolean isInactiveMode) {
		super(g, nc, isInactiveMode);
	}

//	public class StyleMap {
//		private Hashtable<OsmPrimitive, List<RecordedOsmGeometries>> recordedStyles = new Hashtable<>();
//
//		public synchronized <T extends OsmPrimitive> void add(T primitive,
//				RecordedOsmGeometries geometry) {
//			// For testing
//			// geometry.draw(((GLGraphics2D)g).getGLContext().getGL().getGL2());
//
//			List<RecordedOsmGeometries> list = recordedStyles.get(primitive);
//			if (list == null) {
//				list = new ArrayList<>();
//				recordedStyles.put(primitive, list);
//			}
//			list.add((RecordedOsmGeometries) geometry);
//		}
//
//		public synchronized ArrayList<RecordedOsmGeometries> getOrderedStyles() {
//			ArrayList<RecordedOsmGeometries> list = new ArrayList<>();
//			for (List<RecordedOsmGeometries> v : recordedStyles.values()) {
//				list.addAll(v);
//			}
//			Collections.sort(list);
//			return list;
//		}
//	}
//
//	// TEMP
//	private StyleMap styleMap = new StyleMap();

//	/**
//	 * This queue recieves an area to process.
//	 * 
//	 * @author michael
//	 *
//	 */
//	private class StyleWorkQueue {
//
//		int activeWorkCycle = 0;
//
//		DataSet data;
//
//		public StyleWorkQueue(DataSet data) {
//			this.data = data;
//		}
//
//		public void setArea(BBox bbox) {
//
//			activeWorkCycle++;
//			addArea(bbox);
//		}
//
//		public void addArea(final BBox bbox) {
//
//			StyleGenerationState sgs = new StyleGenerationState();
//			new NodeSearcher(new StyleGeometryScheduler<Node>(sgs),
//					data, bbox).run();
//			new WaySearcher(new StyleGeometryScheduler<Way>(sgs), data,
//					bbox).run();
//			new RelationSearcher(new StyleGeometryScheduler<Relation>(
//					sgs), data, bbox).run();
//		}
//	}

	/**
	 * @author michael
	 *
	 */
	public static class StyleGenerationState implements ChacheDataSupplier {
		private static final int MAX_GEOMETRIES_GENERATED = 5000;
		private double circum;
		private ViewPosition viewPosition;
		
		int geometriesGenerated = 0;
		private boolean enoughGometriesGenerated;

		private boolean renderVirtualNodes, isInactiveMode;
		
		private NavigatableComponent cacheKey;
		
		public StyleGenerationState(double circum, ViewPosition viewPosition,
				boolean renderVirtualNodes, boolean isInactiveMode,
				NavigatableComponent cacheKey) {
			super();
			this.circum = circum;
			this.viewPosition = viewPosition;
			this.renderVirtualNodes = renderVirtualNodes;
			this.isInactiveMode = isInactiveMode;
			this.cacheKey = cacheKey;
		}
		
		@Override
		public NavigatableComponent getCacheKey() {
			return cacheKey;
		}

//		public StyleMap getStyleReceiver() {
//			return styleMap;
//		}

		public boolean renderVirtualNodes() {
			return renderVirtualNodes;
		}

		public boolean isInactiveMode() {
			return isInactiveMode;
		}

		@Override
		public double getCircum() {
			return circum;
		}

		public ViewPosition getViewPosition() {
			return viewPosition;
		}
		
		/**
		 * Tests if we should generate one more geometry.
		 * @return
		 */
		public synchronized boolean shouldGenerateGeometry() {
			return !enoughGometriesGenerated;
		}
		
		public synchronized boolean hasGeneratedAllGeometries() {
			System.out.println("Generated: " + geometriesGenerated);
			// Note: Inaccurate by 1 geometry.
			return !enoughGometriesGenerated;
		}

		public synchronized void incrementDrawCounter() {
			geometriesGenerated++;
			if (geometriesGenerated > MAX_GEOMETRIES_GENERATED) {
				enoughGometriesGenerated = true;
			}
		}
	}

//	/**
//	 * Schedules the generation of a bunch of styles/geometries.
//	 * 
//	 * @author michael
//	 *
//	 * @param <T>
//	 */
//	private static class StyleGeometryScheduler<T extends OsmPrimitive>
//			implements OsmPrimitiveHandler<T> {
//		private StyleGenerationState sgs;
//
//		public StyleGeometryScheduler(StyleGenerationState sgs) {
//			this.sgs = sgs;
//		}
//
//		public void receivePrimitives(java.util.List<T> primitives) {
//			// We could now split this into bulks.
//			// TODO: Use different threads.
//			//new StyledGeometryGenerator<>(primitives, sgs).run();
//		}
//	}

	private StyleGenerationManager manager = null;
	
	@Override
	public synchronized void render(final DataSet data, boolean renderVirtualNodes,
			Bounds bounds) {
		if (manager == null) {
			manager = new StyleGenerationManager(data);
		} else if (!manager.usesDataSet(data)) {
			throw new IllegalArgumentException("Wrong DataSet provided.");
		}

		BBox bbox = bounds.toBBox();
		getSettings(renderVirtualNodes);
		data.getReadLock().lock();
		try {
			long time1 = System.currentTimeMillis();
//			StyleWorkQueue styleWorkQueue = new StyleWorkQueue(data);
//			styleWorkQueue.setArea(bbox);
			StyleGenerationState sgs = new StyleGenerationState(getCircum(), ViewPosition.from(nc), renderVirtualNodes, isInactiveMode, nc);
			List<RecordedOsmGeometries> geometries = manager.getDrawGeometries(bbox, sgs);
			long time2 = System.currentTimeMillis();

			DrawStats.reset();
			
			GL2 gl = ((GLGraphics2D) g).getGLContext().getGL().getGL2();
			GLState state = new GLState(gl);
			state.initialize(ViewPosition.from(nc));
			for (RecordedOsmGeometries s : geometries) {
				s.draw(gl, state);
			}
			state.done();
			long time4 = System.currentTimeMillis();

			drawVirtualNodes(data, bbox);
			long time5 = System.currentTimeMillis();
			System.out.println("Create styles: " + (time2 - time1) + ", draw: "
					+ (time4 - time2) + ", draw virtual: " + (time5 - time4));
			DrawStats.printStats();

			if (!sgs.hasGeneratedAllGeometries()) {
				// Repaint again to collect the rest of the geometries.
				nc.repaint();
			}
			
		} finally {
			data.getReadLock().unlock();
		}
	}

}
