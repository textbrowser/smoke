UNAME := $(shell uname)

ifeq ($(UNAME), Linux)
	MAKEFILE=Makefile.linux
else
	MAKEFILE=Makefile.windows
endif

all:
	$(MAKE) -f $(MAKEFILE)

clean:
	$(MAKE) -f $(MAKEFILE) clean

clear-smoke:
	$(MAKE) -f $(MAKEFILE) clear-smoke

copy-apk:
	$(MAKE) -f $(MAKEFILE) copy-apk

debug-with-source:
	$(MAKE) -f $(MAKEFILE) debug-with-source

distclean:
	$(MAKE) -f $(MAKEFILE) distclean

kill-adb-server:
	$(MAKE) -f $(MAKEFILE) kill-adb-server

kill-gradle-daemon:
	$(MAKE) -f $(MAKEFILE) kill-gradle-daemon

list-devices:
	$(MAKE) -f $(MAKEFILE) list-devices

list-files:
	$(MAKE) -f $(MAKEFILE) list-files

load-apk:
	$(MAKE) -f $(MAKEFILE) load-apk

pull-database:
	$(MAKE) -f $(MAKEFILE) pull-database

purge:
	$(MAKE) -f $(MAKEFILE) purge

remove-database:
	$(MAKE) -f $(MAKEFILE) remove-database

stop-smoke:
	$(MAKE) -f $(MAKEFILE) stop-smoke
