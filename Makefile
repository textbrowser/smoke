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

distclean: clean kill-adb-server
	$(MAKE) -f $(MAKEFILE) distclean

kill-adb-server:
	$(MAKE) -f $(MAKEFILE) kill-adb-server

launch-emulator:
	$(MAKE) -f $(MAKEFILE) launch-emulator

load-apk:
	$(MAKE) -f $(MAKEFILE) load-apk

pull-database:
	$(MAKE) -f $(MAKEFILE) pull-database

purge:
	$(MAKE) -f $(MAKEFILE) purge
