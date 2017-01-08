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

distclean:
	$(MAKE) -f $(MAKEFILE) distclean

kill-adb-server:
	$(MAKE) -f $(MAKEFILE) kill-adb-server

kill-gradle-daemon:
	$(MAKE) -f $(MAKEFILE) kill-gradle-daemon

launch-emulator:
	$(MAKE) -f $(MAKEFILE) launch-emulator

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
