PROJECT = contextual
DEPENDENCIES =
MODULES = core data

lib/$(PROJECT).jar: lib compile
	jar -cf lib/$(PROJECT).jar -C bin $(PROJECT)

lib/%.jar: lib
	cd $* && make
	cp $*/lib/*.jar lib/

lib:
	mkdir -p lib

compile: $(DEPENDENCIES)
	@scalac -version | grep 'version 2\.12\.' > /dev/null || echo "scalac is not version 2.12.x"
	mkdir -p bin
	$(foreach MODULE,$(MODULES),scalac -unchecked -feature -d bin -cp bin:lib/'*' src/$(MODULE)/*.scala;)

.PHONY: compile
