package org.openstreetmap.josm.gsoc2015.opengl.osm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gsoc2015.opengl.geometrycache.GeometryMerger;
import org.openstreetmap.josm.gsoc2015.opengl.geometrycache.RecordedOsmGeometries;

/**
 * This is a cache of style geometries.
 * <p>
 * It allows you to query the geometries for a given primitive.
 * <p>
 * This cache is optimized for beeing accessed during one draw phase. It allows
 * asynchronous geometzry generation but geometry fetching should be done in
 * frames.
 * <p>
 * Each frame is initiated by calling {@link #startFrame()}. The cache is then
 * prepared to receive multiple request calls.
 * <p>
 * On each request call, the geometries for this frame can be scheduled for
 * collection using
 * {@link #requestOrCreateGeometry(OsmPrimitive, StyledGeometryGenerator)}
 * <p>
 * When the frame has ended, {@link #endFrame()} returns a list of all received
 * geometries.
 * 
 * 
 * @author Michael Zangl
 *
 */
public class StyleGeometryCache {
	private Hashtable<OsmPrimitive, List<RecordedOsmGeometries>> recordedForPrimitive = new Hashtable<>();

	private Set<RecordedOsmGeometries> collectedForFrame = Collections
			.synchronizedSet(new HashSet<RecordedOsmGeometries>());

	private GeometryMerger collectedForFrameMerger;

	public void invalidateAll() {
		recordedForPrimitive.clear();
	}

	public void startFrame() {
		collectedForFrame.clear();
		collectedForFrameMerger = new GeometryMerger();
	}

	public ArrayList<RecordedOsmGeometries> endFrame() {
		// get all geometries from merger.
		ArrayList<RecordedOsmGeometries> list = new ArrayList<>(
				collectedForFrame);
		ArrayList<RecordedOsmGeometries> fromMerger = collectedForFrameMerger
				.getGeometries();
		System.out.println("Received " + list.size() + " geometries from cache and " + fromMerger.size() + " generated this frame.");
		
		putGeometries(fromMerger);
		list.addAll(fromMerger);
		collectedForFrameMerger = null;
		collectedForFrame.clear();
		return list;
	}

	/**
	 * Requests a (valid) geometry for that primitive.
	 * 
	 * @param primitive
	 *            The primitive.
	 * @return The geometry or <code>null</code> if none is available
	 */
	public List<RecordedOsmGeometries> getGeometry(OsmPrimitive primitive) {
		return recordedForPrimitive.get(primitive);
	}

	/**
	 * <p>
	 * This may only be called for each primitive once in every frame.
	 * 
	 * @param primitive
	 * @param generator
	 * @return
	 */
	public void requestOrCreateGeometry(OsmPrimitive primitive,
			StyledGeometryGenerator<OsmPrimitive> generator) {
		// query primitive
		List<RecordedOsmGeometries> list = recordedForPrimitive.get(primitive);
		// if not exists and generator is set
		if (list == null) {
			if (generator != null) {
				// -- call generator. Generator returns list of geometries
				// -- pass on to merger
				List<RecordedOsmGeometries> geometries = generator
						.runFor(primitive);
				collectedForFrameMerger.addMergeables(geometries);
			} else {
				// if not exists and no generator is set
				// -- schedule for background generation
			}
		} else {
			// -- add the geometries to collectedForFrame
			collectedForFrame.addAll(list);
		}

	}

	private void putGeometries(Collection<RecordedOsmGeometries> geometries) {
		System.out.println("There are " + recordedForPrimitive.size() + " geos in cache");
		for (RecordedOsmGeometries g : geometries) {
			putGeometry(g);
		}
		System.out.println("There are " + recordedForPrimitive.size() + " geos in cache");
	}

	private synchronized void putGeometry(RecordedOsmGeometries geometry) {
		for (OsmPrimitive primitive : geometry.getPrimitives()) {
			List<RecordedOsmGeometries> list = recordedForPrimitive
					.get(primitive);
			if (list == null) {
				list = new ArrayList<>();
				recordedForPrimitive.put(primitive, list);
			}
			list.add(geometry);
		}
	}
}