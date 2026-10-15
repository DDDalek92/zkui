IMAGE ?= zkui:local

.PHONY: all build publish
all: build

build:
	docker build -f docker/Dockerfile -t "$(IMAGE)" .

# Publishing is an explicit operator action; no guessed registry or implicit push.
publish:
	@test -n "$(PUBLISH_IMAGE)" || { echo 'Set PUBLISH_IMAGE=registry/namespace/zkui:tag'; exit 2; }
	docker tag "$(IMAGE)" "$(PUBLISH_IMAGE)"
	docker push "$(PUBLISH_IMAGE)"
