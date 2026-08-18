dist:
	ant dist

deploy:
	cp dist/replace-nodes.jar ~/.josm/plugins/
