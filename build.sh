#!/bin/zsh

PROJECT=contextual

source ../build.zsh

build core && \
build data -cp $PROJECT-core.jar && \
build tests -cp $PROJECT-core.jar:$PROJECT-data.jar:../estrapade/estrapade-macros.jar:../estrapade/estrapade-core.jar && \
runtests tests -cp $PROJECT-core.jar:$PROJECT-data.jar:$PROJECT-tests.jar:../estrapade/estrapade-macros.jar:../estrapade/estrapade-core.jar
