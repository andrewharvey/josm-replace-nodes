# josm-replace-nodes

A JOSM plugin to replace the nodes of a way, with the nodes of another way.

## Background
The utilsplugin2 JOSM plugin has a neat "Replace geometry" tool, that allows you to draw a new way, and use it to "replace geometry" of an existing uploaded object. This practice helps to [keep the history](https://wiki.openstreetmap.org/wiki/Keep_the_history).

However that tool will refuse to operate on two existing ways, it requires one to be newer, but sometimes when mapping we want to use the geometry from one way, but retain the way history from other.

Hence this tool was born, by way of a few LLM prompts, to fulfil that requirement.

## How to build and install

   make build
   make install
