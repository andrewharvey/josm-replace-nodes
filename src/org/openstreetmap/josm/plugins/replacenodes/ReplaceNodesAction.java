package org.openstreetmap.josm.plugins.replacenodes;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.ChangeNodesCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.tools.Shortcut;

public class ReplaceNodesAction extends JosmAction {

    public ReplaceNodesAction() {
        super(tr("Replace nodes"), (String) null,
                tr("Replaces the node list of the first selected way with the nodes of the second"),
                Shortcut.registerShortcut("replacenodes:replace",
                        tr("Tools: {0}", tr("Replace nodes")),
                        KeyEvent.VK_R, Shortcut.ALT_CTRL_SHIFT),
                true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        DataSet ds = getLayerManager().getEditDataSet();
        if (ds == null) {
            return;
        }

        List<Way> ways = SelectionOrderTracker.getInstance().ordered(ds.getSelectedWays());
        if (ways.size() != 2) {
            warn(tr("Please select exactly two ways."));
            return;
        }

        Way target = ways.get(0);
        Way source = ways.get(1);

        if (target.isIncomplete() || source.isIncomplete()
                || target.hasIncompleteNodes() || source.hasIncompleteNodes()) {
            warn(tr("One of the ways is incomplete. Download it fully first."));
            return;
        }
        if (source.getNodesCount() < 2) {
            warn(tr("The source way has fewer than two nodes."));
            return;
        }

        int choice = JOptionPane.showOptionDialog(MainApplication.getMainFrame(),
                tr("Replace the nodes of\n  {0}\nwith the nodes of\n  {1}?",
                        target.getDisplayName(org.openstreetmap.josm.data.osm.DefaultNameFormatter.getInstance()),
                        source.getDisplayName(org.openstreetmap.josm.data.osm.DefaultNameFormatter.getInstance())),
                tr("Replace nodes"), JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                new Object[] {tr("Replace"), tr("Swap roles"), tr("Cancel")}, tr("Replace"));

        if (choice == 1) {
            Way tmp = target;
            target = source;
            source = tmp;
        } else if (choice != 0) {
            return;
        }

        replace(ds, target, source);
    }

    private void replace(DataSet ds, Way target, Way source) {
        List<Node> oldNodes = new ArrayList<>(target.getNodes());
        List<Node> newNodes = new ArrayList<>(source.getNodes());

        if (shouldReverse(target, source)) {
            Collections.reverse(newNodes);
        }

        List<Command> cmds = new ArrayList<>();
        cmds.add(new ChangeNodesCommand(ds, target, newNodes));

        // Transfer relation memberships from source to target before deleting source.
        for (OsmPrimitive ref : source.getReferrers()) {
            if (!(ref instanceof Relation)) continue;
            Relation rel = (Relation) ref;
            List<RelationMember> members = new ArrayList<>(rel.getMembers());
            boolean changed = false;
            for (int i = 0; i < members.size(); i++) {
                RelationMember m = members.get(i);
                if (!m.getMember().equals(source)) continue;
                String role = m.getRole();
                boolean targetAlreadyPresent = members.stream()
                        .anyMatch(x -> x.getMember().equals(target) && x.getRole().equals(role));
                if (targetAlreadyPresent) {
                    members.remove(i--);
                } else {
                    members.set(i, new RelationMember(role, target));
                }
                changed = true;
            }
            if (changed) {
                Relation updated = new Relation(rel);
                updated.setMembers(members);
                cmds.add(new ChangeCommand(rel, updated));
            }
        }

        // The source way is now redundant. Its nodes are kept: they belong to the target now.
        cmds.add(new DeleteCommand(ds, Collections.singleton(source)));

        // Nodes the target used to own, which nothing else will reference afterwards.
        List<OsmPrimitive> orphans = findOrphans(oldNodes, newNodes, target, source);
        if (!orphans.isEmpty()) {
            cmds.add(new DeleteCommand(ds, orphans));
        }

        UndoRedoHandler.getInstance().add(new SequenceCommand(tr("Replace nodes"), cmds));
        ds.setSelected(Collections.singleton(target));

        new Notification(tr("Replaced geometry with {0} nodes; removed {1} orphaned node(s).",
                newNodes.size(), orphans.size())).show();
    }

    /**
     * Predicts which of the target''s old nodes become unreferenced once the change command
     * and the deletion of the source way have run.
     */
    private List<OsmPrimitive> findOrphans(List<Node> oldNodes, List<Node> newNodes,
                                           Way target, Way source) {
        Set<Node> kept = new HashSet<>(newNodes);
        Set<Node> seen = new HashSet<>();
        List<OsmPrimitive> orphans = new ArrayList<>();
        for (Node n : oldNodes) {
            if (kept.contains(n) || n.isTagged() || n.isDeleted() || !seen.add(n)) {
                continue;
            }
            boolean usedElsewhere = n.getReferrers().stream()
                    .anyMatch(p -> !p.equals(target) && !p.equals(source));
            if (!usedElsewhere) {
                orphans.add(n);
            }
        }
        return orphans;
    }

    /** Flips the new node list if the source runs opposite to the target. */
    private boolean shouldReverse(Way target, Way source) {
        if (target.isClosed() || source.isClosed() || target.getNodesCount() < 2) {
            return false;
        }
        LatLon ts = target.firstNode().getCoor();
        LatLon te = target.lastNode().getCoor();
        LatLon ss = source.firstNode().getCoor();
        LatLon se = source.lastNode().getCoor();
        if (ts == null || te == null || ss == null || se == null) {
            return false;
        }
        double sameDir = ts.greatCircleDistance(ss) + te.greatCircleDistance(se);
        double flipped = ts.greatCircleDistance(se) + te.greatCircleDistance(ss);
        return flipped < sameDir;
    }

    private void warn(String message) {
        new Notification(message).setIcon(JOptionPane.WARNING_MESSAGE).show();
    }

    @Override
    protected void updateEnabledState() {
        DataSet ds = getLayerManager().getEditDataSet();
        setEnabled(ds != null && ds.getSelectedWays().size() == 2);
    }

    @Override
    protected void updateEnabledState(Collection<? extends OsmPrimitive> selection) {
        updateEnabledState();
    }
}
