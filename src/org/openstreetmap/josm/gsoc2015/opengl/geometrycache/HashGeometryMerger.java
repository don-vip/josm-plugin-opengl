package org.openstreetmap.josm.gsoc2015.opengl.geometrycache;

import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map.Entry;

import org.openstreetmap.josm.data.osm.OsmPrimitive;

/**
 * This is a geometry merger that uses the hashes of geometries to find the best
 * merge group in which a new geometry should be merged.
 *
 * @author Michael Zangl
 *
 */
public class HashGeometryMerger extends GeometryMerger {
	private static final int ACTIVE_GROUPS = 16;
	private static final boolean DEBUG = false;
	/**
	 * A list of merge groups that are currently open.
	 */
	private final MergeGroup[] activeMergeGroups = new MergeGroup[ACTIVE_GROUPS];
	/**
	 * For each open merge group, this list contains the sum of hashes stored in
	 * {@link #hashUses}.
	 */
	private final int[] mergeGroupHashCount = new int[ACTIVE_GROUPS];
	/**
	 * This map stores how often an active merge group uses a given hash.
	 * <p>
	 * It is a mapping (hashCode, mergeGroupIndex) -> number of used hashes.
	 */
	private final Hashtable<Integer, byte[]> hashUses = new Hashtable<>();

	private int getBestMergeables(Collection<RecordedOsmGeometries> geos) {
		final int[] hashUsedCount = new int[ACTIVE_GROUPS];
		for (final RecordedOsmGeometries g : geos) {
			for (final int h : g.getCombineHashes()) {
				final byte[] hashUsed = hashUses.get(h);
				if (hashUsed == null) {
					continue;
				}
				for (int i = 0; i < ACTIVE_GROUPS; i++) {
					hashUsedCount[i] += hashUsed[i] & 0xff;
				}
			}
		}

		// Now do a quick trick: We place the hash used count in the upper half
		// of
		// the int, the slot index in the lower half.

		for (int i = 0; i < ACTIVE_GROUPS; i++) {
			final int weightedCount = hashUsedCount[i] * 256
					/ (mergeGroupHashCount[i] + 1);
			if (DEBUG) {
				System.out
				.println("Rated for slot " + i + ": " + weightedCount);
			}
			hashUsedCount[i] = 0x7fffffff & (weightedCount << 8) + i;
		}
		Arrays.sort(hashUsedCount);

		for (int i = ACTIVE_GROUPS - 1; i > ACTIVE_GROUPS - 6; i--) {
			final int slotToUse = hashUsedCount[i] & (1 << 8) - 1;
			final MergeGroup group = activeMergeGroups[slotToUse];
			if (group != null) {
				return slotToUse;
			}
		}
		return -1;
	}

	private int replacementClock = 0;

	@Override
	public synchronized void addMergeables(OsmPrimitive primitive,
			Collection<RecordedOsmGeometries> geometries) {
		if (DEBUG) {
			System.out.println("SEARCHING FOR " + geometries);
			dump();
		}
		final int groupIndex = getBestMergeables(geometries);
		if (groupIndex < 0) {
			final MergeGroup group = new MergeGroup();
			// now let's add a new group
			for (final byte[] v : hashUses.values()) {
				v[replacementClock] = 0;
			}
			activeMergeGroups[replacementClock] = group;
			int hashes = 0;
			for (final RecordedOsmGeometries g : geometries) {
				final int[] combineHashes = g.getCombineHashes();
				for (final int h : combineHashes) {
					byte[] uses = hashUses.get(h);
					if (uses == null) {
						uses = new byte[ACTIVE_GROUPS];
						hashUses.put(h, uses);
					}
					uses[replacementClock]++;
				}
				hashes += combineHashes.length;
			}

			mergeGroupHashCount[replacementClock] = hashes;
			group.merge(primitive, geometries);

			replacementClock++;
			if (replacementClock >= ACTIVE_GROUPS) {
				cleanUnusedHashes();
				replacementClock = 0;
			}
			mergeGroups.add(group);
		} else {
			final MergeGroup group = activeMergeGroups[groupIndex];
			group.merge(primitive, geometries);
			if (!group.moreMergesRecommended()) {
				activeMergeGroups[groupIndex] = null;
				// rates this group really low.
				mergeGroupHashCount[groupIndex] = Integer.MAX_VALUE - 1;
			} else {
				// TODO: Add the new hashes for the old group. (?)
			}
		}
	}

	private void cleanUnusedHashes() {
		for (final Iterator<Entry<Integer, byte[]>> iterator = hashUses.entrySet()
				.iterator(); iterator.hasNext();) {
			final Entry<Integer, byte[]> k = iterator.next();
			if (isEmpty(k.getValue())) {
				iterator.remove();
			}
		}
	}

	private boolean isEmpty(byte[] value) {
		for (final byte b : value) {
			if (b != 0) {
				return false;
			}
		}
		return true;
	}

	private void dump() {
		for (int i = 0; i < ACTIVE_GROUPS; i++) {
			System.out.println("Group " + i + ": " + activeMergeGroups[i]);
			System.out.print("    Hashes:");
			for (final Entry<Integer, byte[]> h : hashUses.entrySet()) {
				if (h.getValue()[i] > 0) {
					System.out.print(" " + Integer.toHexString(h.getKey())
							+ "x" + h.getValue()[i]);
				}
			}
			System.out.println();
			System.out.println("    Total hash count: "
					+ mergeGroupHashCount[i]);
		}
	}

}
