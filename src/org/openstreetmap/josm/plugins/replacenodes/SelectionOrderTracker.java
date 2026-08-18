package org.openstreetmap.josm.plugins.replacenodes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import org.openstreetmap.josm.data.osm.DataSelectionListener;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.event.SelectionEventManager;

/** Remembers the order in which primitives entered the current selection. */
public final class SelectionOrderTracker implements DataSelectionListener {

    private static final SelectionOrderTracker INSTANCE = new SelectionOrderTracker();

    private final LinkedHashSet<OsmPrimitive> order = new LinkedHashSet<>();

    private SelectionOrderTracker() { }

    public static SelectionOrderTracker getInstance() {
        return INSTANCE;
    }

    public void install() {
        SelectionEventManager.getInstance().addSelectionListener(this);
    }

    @Override
    public synchronized void selectionChanged(SelectionChangeEvent event) {
        Collection<? extends OsmPrimitive> sel = event.getSelection();
        order.retainAll(sel);   // drop deselected, keep existing order
        order.addAll(sel);      // append newly selected at the end
    }

    /**
     * @param selectedWays the currently selected ways
     * @return the same ways, ordered by when they were selected
     */
    public synchronized List<Way> ordered(Collection<Way> selectedWays) {
        List<Way> result = new ArrayList<>(selectedWays.size());
        for (OsmPrimitive p : order) {
            if (p instanceof Way && selectedWays.contains(p)) {
                result.add((Way) p);
            }
        }
        for (Way w : selectedWays) {          // anything we somehow missed
            if (!result.contains(w)) {
                result.add(w);
            }
        }
        return result;
    }
}
