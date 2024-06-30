# Notes on compiling ACME Cross Assembler

Acme is a very handy macro assembler for the 6502 family of processors.  It is also very easy to build.  Because of this, we can port ACME to Java without even having to alter the source thanks to NestedVM.

A word of caution: NestedVM is very old and very unmaintained, so it goes without saying that there be dragons.  Re-transpiling new versions of ACME is still possible (as of Feb 2024) but it is not easy.

## Getting set up

First use a fork of NestedVM (the original is buggy and not maintained at all, wherease newer forks are at least a little better)

- https://github.com/bgould/nestedvm/tree/master

Next, check the urls in the upstream/Makefile to ensure they are valid.  Currently, I found that I needed to make the following changes:

```
diff --git a/upstream/Makefile b/upstream/Makefile
index 83eaa0a..d1f1cbb 100644
--- a/upstream/Makefile
+++ b/upstream/Makefile
@@ -211,7 +211,7 @@ configure_binutils = --target=mips-unknown-elf --disable-werror
 ## newlib ##############################################################################
 
 version_newlib = 1.20.0
-url_newlib = ftp://sources.redhat.com/pub/newlib/newlib-$(version_newlib).tar.gz
+url_newlib = ftp://sourceware.org/pub/newlib/newlib-$(version_newlib).tar.gz
 patches_newlib = newlib-mips.patch newlib-tzset.patch newlib-malloc.patch newlib-nomemcpy.patch newlib-unix.patch newlib-unistd.patch newlib-nestedvm-define.patch newlib-sdata.patch newlib-new.patch
 configure_newlib = --enable-multilib --target=mips-unknown-elf
 
@@ -236,13 +236,14 @@ tasks/build_openbsdglob: tasks/download_openbsdglob tasks/build_newlib
 
 ## regex ##############################################################################
 
-url_regex = http://www.arglist.com/regex/files/regex3.8a.tar.gz
+#url_regex = http://www.arglist.com/regex/files/regex3.8a.tar.gz
+url_regex = https://github.com/garyhouston/regex/archive/refs/tags/alpha3.8p1.tar.gz
 
 tasks/build_regex: tasks/download_regex tasks/build_newlib
 	@mkdir -p $(usr)/mips-unknown-elf/{include,lib}
 	mkdir -p build/regex build/regex/fake
 	cd build && \
-		tar xvzf ../download/regex3.8a.tar.gz && cd regex && \
+		tar xvzf ../download/alpha3.8p1.tar.gz && cd regex-alpha3.8p1 && \
 		make CC=mips-unknown-elf-gcc CFLAGS="-I. $(MIPS_CFLAGS)" regcomp.o regexec.o regerror.o regfree.o && \
 		mips-unknown-elf-ar cr libregex.a regcomp.o regexec.o regerror.o regfree.o && \
 		mips-unknown-elf-ranlib libregex.a && \
```

From here it's a matter of running Make from the main folder and waiting a long time (approx 2 hours, it has to compile GCC)

Next: Use these commands to build the rest of the things you might need:

```
make env.sh
```
This will create a convenient shell script that demonstrates all the GCC and linker flags you'll need for Acme, so it's good for future reference.

```
make test
```
Quick sanity check.  If you look closely it reveals how to use the nestedvm compiler... sort of.

## Building ACME

With the MIPS GCC binary available, now grab the source for ACME you want to use and extract to another folder.  Modify the Makefile like so:
```
CC=mips-unknown-elf-gcc
CXX=mips-unknown-elf-g++
AS=mips-unknown-elf-as
AR=mips-unknown-elf-ar
LD=mips-unknown-elf-ld
RANLIB=mips-unknown-elf-ranlib
CFLAGS= -O2 -mmemcpy -ffunction-sections -fdata-sections -falign-functions=512 -fno-rename-registers -fno-schedule-insns -fno-delayed-branch -Wstrict-prototypes -march=mips1 -specs=/Users/brobert/Documents/code/nestedvm/upstream/install/mips-unknown-elf/lib/crt0-override.spec -static -mmemcpy --static -Wl,--gc-sect>
```
Note that I used -O2 not -O3.  It probably doesn't make much of a functional difference but -O3 produced something that was 20% larger.

Also remove the `strip acme` line because nestedvm needs the symbol table.

## Converting to Java
After you build acme, next you need to transpile it to java.  I used the following command to do that:
```
java -cp ../nestedvm/build:../nestedvm/upstream/build/classgen/build org.ibex.nestedvm.Compiler -outformat java -outfile AcmeCrossAssembler.java -o unixRuntime jace.assembly.AcmeCrossAssembler acme
```

This produces a file called AcmeCrossAssembler.java which can replace the current one.  You should run tests via `mvn test` in Jace to make sure that ACME is working properly.  Since the CPU unit tests use it heavily, that's a pretty good test for Acme as well. :)

