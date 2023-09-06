commitCount := $(shell git rev-list --count HEAD)
shortSha := $(shell git rev-parse --short HEAD)
test:
	clj -M:test

release:
	sed -i '' -e "s/io.github.lukaszkorecki.*/io.github.lukaszkorecki\/tolkien \{:git\/tag \"v0.1.${commitCount}\" :git\/sha \"${shortSha}\"\}/" README.md
	git add README.md
	git tag -a v0.1.${commitCount} -m "Version 0.1.${commitCount}"
	git commit -m 'Update release coordinates'
	git push --tags && git push


.PHONY: test
