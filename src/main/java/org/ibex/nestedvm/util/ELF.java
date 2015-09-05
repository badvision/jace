// Copyright 2000-2005 the Contributors, as shown in the revision logs.
// Licensed under the Apache Public Source License 2.0 ("the License").
// You may not use this file except in compliance with the License.

package org.ibex.nestedvm.util;

import java.io.*;

public class ELF {
    private static final int ELF_MAGIC = 0x7f454c46; // '\177', 'E', 'L', 'F'

    public static final int ELFCLASSNONE = 0;
    public static final int ELFCLASS32 = 1;
    public static final int ELFCLASS64 = 2;

    public static final int ELFDATANONE = 0;
    public static final int ELFDATA2LSB = 1;
    public static final int ELFDATA2MSB = 2;     
    
    public static final int SHT_SYMTAB = 2;
    public static final int SHT_STRTAB = 3;
    public static final int SHT_NOBITS = 8;
    
    public static final int SHF_WRITE = 1;
    public static final int SHF_ALLOC = 2;
    public static final int SHF_EXECINSTR = 4;
    
    public static final int PF_X = 0x1;
    public static final int PF_W = 0x2;
    public static final int PF_R = 0x4;
    
    public static final int PT_LOAD = 1;

    public static final short ET_EXEC = 2;
    public static final short EM_MIPS = 8;

    
    private Seekable data;
    
    public ELFIdent ident;
    public ELFHeader header;
    public PHeader[] pheaders;
    public SHeader[] sheaders;
    
    private byte[] stringTable;
    
    private boolean sectionReaderActive;
    
    
    private void readFully(byte[] buf) throws IOException {
        int len = buf.length;
        int pos = 0;
        while(len > 0) {
            int n = data.read(buf,pos,len);
            if(n == -1) throw new IOException("EOF");
            pos += n;
            len -= n;
        }
    }
    
    private int readIntBE() throws IOException {
        byte[] buf = new byte[4];
        readFully(buf);
        return ((buf[0]&0xff)<<24)|((buf[1]&0xff)<<16)|((buf[2]&0xff)<<8)|((buf[3]&0xff)<<0);
    }
    private int readInt() throws IOException {
        int x = readIntBE();
        if(ident!=null && ident.data == ELFDATA2LSB) 
            x = ((x<<24)&0xff000000) | ((x<<8)&0xff0000) | ((x>>>8)&0xff00) | ((x>>24)&0xff);
        return x;
    }
    
    private short readShortBE() throws IOException {
        byte[] buf = new byte[2];
        readFully(buf);
        return (short)(((buf[0]&0xff)<<8)|((buf[1]&0xff)<<0));
    }
    private short readShort() throws IOException {
        short x = readShortBE();
        if(ident!=null && ident.data == ELFDATA2LSB) 
            x = (short)((((x<<8)&0xff00) | ((x>>8)&0xff))&0xffff);
        return x;
    }
    
    private byte readByte() throws IOException {
        byte[] buf = new byte[1];
        readFully(buf);
        return buf[0];
    }
        
    public class ELFIdent {
        public byte klass;   
        public byte data;
        public byte osabi;
        public byte abiversion;
                
        ELFIdent() throws IOException {
            if(readIntBE() != ELF_MAGIC) throw new ELFException("Bad Magic");
            
            klass = readByte();
            if(klass != ELFCLASS32) throw new ELFException("org.ibex.nestedvm.util.ELF does not suport 64-bit binaries");
            
            data = readByte();
            if(data != ELFDATA2LSB && data != ELFDATA2MSB) throw new ELFException("Unknown byte order");
            
            readByte(); // version
            osabi = readByte();
            abiversion = readByte();
            for(int i=0;i<7;i++) readByte(); // padding
        }
    }
    
    public class ELFHeader {
        public short type;        
        public short machine;
        public int version;
        public int entry;
        public int phoff;
        public int shoff;
        public int flags;
        public short ehsize;
        public short phentsize;
        public short phnum;
        public short shentsize;
        public short shnum;
        public short shstrndx;

        ELFHeader() throws IOException {
            type = readShort();
            machine = readShort();
            version = readInt();
            if(version != 1) throw new ELFException("version != 1");
            entry = readInt();
            phoff = readInt();
            shoff = readInt();
            flags = readInt();
            ehsize = readShort();
            phentsize = readShort();
            phnum = readShort();
            shentsize = readShort();
            shnum = readShort();
            shstrndx = readShort();
        }
    }
    
    public class PHeader {
        public int type;
        public int offset;
        public int vaddr;
        public int paddr;
        public int filesz;
        public int memsz;
        public int flags;
        public int align;
        
        PHeader() throws IOException {
            type = readInt();
            offset = readInt();
            vaddr = readInt();
            paddr = readInt();
            filesz = readInt();
            memsz = readInt();
            flags = readInt();
            align = readInt();
            if(filesz > memsz) throw new ELFException("ELF inconsistency: filesz > memsz (" + toHex(filesz) + " > " + toHex(memsz) + ")");
        }
        
        public boolean writable() { return (flags & PF_W) != 0; }
        
        public InputStream getInputStream() throws IOException {
            return new BufferedInputStream(new SectionInputStream(
                offset,offset+filesz));
        }
    }
    
    public class SHeader {
        int nameidx;
        public String name;
        public int type;
        public int flags;
        public int addr;
        public int offset;
        public int size;
        public int link;
        public int info;
        public int addralign;
        public int entsize;
        
        SHeader() throws IOException {
            nameidx = readInt();
            type = readInt();
            flags = readInt();
            addr = readInt();
            offset = readInt();
            size = readInt();
            link = readInt();
            info = readInt();
            addralign = readInt();
            entsize = readInt();
        }
        
        public InputStream getInputStream() throws IOException {
            return new BufferedInputStream(new SectionInputStream(
                offset, type == SHT_NOBITS ? 0 : offset+size));
        }
        
        public boolean isText() { return name.equals(".text"); }
        public boolean isData() { return name.equals(".data") || name.equals(".sdata") || name.equals(".rodata") || name.equals(".ctors") || name.equals(".dtors"); }
        public boolean isBSS() { return name.equals(".bss") || name.equals(".sbss"); }
    }
    
    public ELF(String file) throws IOException, ELFException { this(new Seekable.File(file,false)); }
    public ELF(Seekable data) throws IOException, ELFException {
        this.data = data;
        ident = new ELFIdent();
        header = new ELFHeader();
        pheaders = new PHeader[header.phnum];
        for(int i=0;i<header.phnum;i++) {
            data.seek(header.phoff+i*header.phentsize);
            pheaders[i] = new PHeader();
        }
        sheaders = new SHeader[header.shnum];
        for(int i=0;i<header.shnum;i++) {
            data.seek(header.shoff+i*header.shentsize);
            sheaders[i] = new SHeader();
        }
        if(header.shstrndx < 0 || header.shstrndx >= header.shnum) throw new ELFException("Bad shstrndx");
        data.seek(sheaders[header.shstrndx].offset);
        stringTable = new byte[sheaders[header.shstrndx].size];
        readFully(stringTable);
        
        for(int i=0;i<header.shnum;i++) {
            SHeader s = sheaders[i];
            s.name = getString(s.nameidx);
        }
    }
    
    private String getString(int off) { return getString(off,stringTable); }
    private String getString(int off,byte[] strtab) {
        StringBuffer sb = new StringBuffer();
        if(off < 0 || off >= strtab.length) return "<invalid strtab entry>";
        while(off >= 0 && off < strtab.length && strtab[off] != 0) sb.append((char)strtab[off++]);
        return sb.toString();
    }
    
    public SHeader sectionWithName(String name) {
        for(int i=0;i<sheaders.length;i++)
            if(sheaders[i].name.equals(name))
                return sheaders[i];
        return null;
    }
    
    public class ELFException extends IOException { ELFException(String s) { super(s); } }
    
    private class SectionInputStream extends InputStream {
        private int pos;
        private int maxpos;
        SectionInputStream(int start, int end) throws IOException {
            if(sectionReaderActive)
                throw new IOException("Section reader already active");
            sectionReaderActive = true;
            pos = start;
            data.seek(pos);
            maxpos = end;
        }
        
        private int bytesLeft() { return maxpos - pos; }
        public int read() throws IOException {
            byte[] buf = new byte[1];
            return read(buf,0,1) == -1 ? -1 : (buf[0]&0xff);
        }
        public int read(byte[] b, int off, int len) throws IOException {
            int n = data.read(b,off,Math.min(len,bytesLeft())); if(n > 0) pos += n; return n;
        }
        public void close() { sectionReaderActive = false; }
    }
    
    private Symtab _symtab;
    public Symtab getSymtab() throws IOException {
        if(_symtab != null) return _symtab;
        
        if(sectionReaderActive) throw new ELFException("Can't read the symtab while a section reader is active");
        
        SHeader sh = sectionWithName(".symtab");
        if(sh == null || sh.type != SHT_SYMTAB) return null;
        
        SHeader sth = sectionWithName(".strtab");
        if(sth == null || sth.type != SHT_STRTAB) return null;
        
        byte[] strtab = new byte[sth.size];
        DataInputStream dis = new DataInputStream(sth.getInputStream());
        dis.readFully(strtab);
        dis.close();
        
        return _symtab = new Symtab(sh.offset, sh.size,strtab);
    }
    
    public class  Symtab {
        public Symbol[] symbols;
        
        Symtab(int off, int size, byte[] strtab) throws IOException {
            data.seek(off);
            int count = size/16;
            symbols = new Symbol[count];
            for(int i=0;i<count;i++) symbols[i] = new Symbol(strtab);
        }
        
        public Symbol getSymbol(String name) {
            Symbol sym = null;
            for(int i=0;i<symbols.length;i++) {
                if(symbols[i].name.equals(name)) {
                    if(sym == null)
                        sym = symbols[i];
                    else
                        System.err.println("WARNING: Multiple symbol matches for " + name);
                }
            }
            return sym;
        }
        
        public Symbol getGlobalSymbol(String name) {
            for(int i=0;i<symbols.length;i++) 
                if(symbols[i].binding == Symbol.STB_GLOBAL && symbols[i].name.equals(name))
                    return symbols[i];
            return null;
        }
    }
    
    public class Symbol {
        public String name;
        public int addr;
        public int size;
        public byte info;
        public byte type;
        public byte binding;
        public byte other;
        public short shndx;
        public SHeader sheader;
        
        public final static int STT_FUNC = 2;
        public final static int STB_GLOBAL = 1;
        
        Symbol(byte[] strtab) throws IOException {
            name = getString(readInt(),strtab);
            addr = readInt();
            size = readInt();
            info = readByte();
            type = (byte)(info&0xf);
            binding = (byte)(info>>4);
            other = readByte();
            shndx = readShort();
        }
    }
    
    private static String toHex(int n) { return "0x" + Long.toString(n & 0xffffffffL, 16); }
    
    /*public static void main(String[] args) throws IOException {
        ELF elf = new ELF(new Seekable.InputStream(new FileInputStream(args[0])));
        System.out.println("Type: " + toHex(elf.header.type));
        System.out.println("Machine: " + toHex(elf.header.machine));
        System.out.println("Entry: " + toHex(elf.header.entry));
        for(int i=0;i<elf.pheaders.length;i++) {
            ELF.PHeader ph = elf.pheaders[i];
            System.out.println("PHeader " + toHex(i));
            System.out.println("\tOffset: " + ph.offset);
            System.out.println("\tVaddr: " + toHex(ph.vaddr));
            System.out.println("\tFile Size: " + ph.filesz);
            System.out.println("\tMem Size: " + ph.memsz);
        }
        for(int i=0;i<elf.sheaders.length;i++) {
            ELF.SHeader sh = elf.sheaders[i];
            System.out.println("SHeader " + toHex(i));
            System.out.println("\tName: " + sh.name);
            System.out.println("\tOffset: " + sh.offset);
            System.out.println("\tAddr: " + toHex(sh.addr));
            System.out.println("\tSize: " + sh.size);
            System.out.println("\tType: " + toHex(sh.type));
        }
        Symtab symtab = elf.getSymtab();
        if(symtab != null) {
            System.out.println("Symbol table:");
            for(int i=0;i<symtab.symbols.length;i++)
                System.out.println("\t" + symtab.symbols[i].name + " -> " + toHex(symtab.symbols[i].addr));
        } else {
            System.out.println("Symbol table: None");
        }
    }*/
}
