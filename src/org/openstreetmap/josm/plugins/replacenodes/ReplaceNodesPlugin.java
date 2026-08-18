package org.openstreetmap.josm.plugins.replacenodes;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

public class ReplaceNodesPlugin extends Plugin {

    public ReplaceNodesPlugin(PluginInformation info) {
        super(info);
        SelectionOrderTracker.getInstance().install();
        MainMenu.add(MainApplication.getMenu().moreToolsMenu, new ReplaceNodesAction());
    }
}
