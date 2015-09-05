// Copyright 2000-2005 the Contributors, as shown in the revision logs.
// Licensed under the Apache Public Source License 2.0 ("the License").
// You may not use this file except in compliance with the License.

package org.ibex.nestedvm;

import org.ibex.nestedvm.util.*;
import java.io.*;
import java.util.*;
import java.net.*;
import java.nio.file.*;
import java.lang.reflect.*; // For lazily linked RuntimeCompiler

// FEATURE: vfork

public abstract class UnixRuntime extends Runtime implements Cloneable {
    /** The pid of this "process" */
    private int pid;
    private UnixRuntime parent;
    public final int getPid() {
        return pid;
    }

    private static final GlobalState defaultGS = new GlobalState();
    private GlobalState gs;
    public void setGlobalState(GlobalState gs) {
        if(state != STOPPED) throw new IllegalStateException("can't change GlobalState when running");
        if(gs == null) throw new NullPointerException("gs is null");
        this.gs = gs;
    }

    /** proceses' current working directory - absolute path WITHOUT leading slash
        "" = root, "bin" = /bin "usr/bin" = /usr/bin */
    private String cwd;

    /** The runtime that should be run next when in state == EXECED */
    private UnixRuntime execedRuntime;

    private Object children; // used only for synchronizatin
    private Vector<UnixRuntime> activeChildren;
    private Vector<UnixRuntime> exitedChildren;

    protected UnixRuntime(int pageSize, int totalPages) {
	    this(pageSize,totalPages,false);
    }

    protected UnixRuntime(int pageSize, int totalPages, boolean exec) {
        super(pageSize,totalPages,exec);

	if(!exec) {
            gs = defaultGS;
            String userdir = Platform.getProperty("user.dir");
            cwd = userdir == null ? null : gs.mapHostPath(userdir);
            if(cwd == null) cwd = "/";
            cwd = cwd.substring(1);
        }
    }

    private static String posixTZ() {
        StringBuffer sb = new StringBuffer();
        TimeZone zone = TimeZone.getDefault();
        int off = zone.getRawOffset() / 1000;
        sb.append(Platform.timeZoneGetDisplayName(zone,false,false));
        if(off > 0) sb.append("-");
        else off = -off;
        sb.append(off/3600);
        off = off%3600;
        if(off > 0) sb.append(":").append(off/60);
        off=off%60;
        if(off > 0) sb.append(":").append(off);
        if(zone.useDaylightTime())
            sb.append(Platform.timeZoneGetDisplayName(zone,true,false));
        return sb.toString();
    }

    private static boolean envHas(String key,String[] environ) {
        for(int i=0; i<environ.length; i++)
            if(environ[i]!=null && environ[i].startsWith(key + "=")) return true;
        return false;
    }

    String[] createEnv(String[] extra) {
        String[] defaults = new String[7];
        int n=0;
        if(extra == null) extra = new String[0];
        String tmp;
        if(!envHas("USER",extra) && Platform.getProperty("user.name") != null)
            defaults[n++] = "USER=" + Platform.getProperty("user.name");
        if(!envHas("HOME",extra) && (tmp=Platform.getProperty("user.home")) != null && (tmp=gs.mapHostPath(tmp)) != null)
            defaults[n++] = "HOME=" + tmp;
        if(!envHas("TMPDIR",extra) && (tmp=Platform.getProperty("java.io.tmpdir")) != null && (tmp=gs.mapHostPath(tmp)) != null)
            defaults[n++] = "TMPDIR=" + tmp;
        if(!envHas("SHELL",extra)) defaults[n++] = "SHELL=/bin/sh";
        if(!envHas("TERM",extra) && !win32Hacks)  defaults[n++] = "TERM=vt100";
        if(!envHas("TZ",extra))    defaults[n++] = "TZ=" + posixTZ();
        if(!envHas("PATH",extra))  defaults[n++] = "PATH=/usr/local/bin:/usr/bin:/bin:/usr/local/sbin:/usr/sbin:/sbin";
        String[] env = new String[extra.length+n];
        for(int i=0; i<n; i++) env[i] = defaults[i];
        for(int i=0; i<extra.length; i++) env[n++] = extra[i];
        return env;
    }

    private static class ProcessTableFullExn extends RuntimeException { }

    void _started() {
        UnixRuntime[] tasks = gs.tasks;
        synchronized(gs) {
            if(pid != 0) {
                UnixRuntime prev = tasks[pid];
                if(prev == null || prev == this || prev.pid != pid || prev.parent != parent)
                    throw new Error("should never happen");
                synchronized(parent.children) {
                    int i = parent.activeChildren.indexOf(prev);
                    if(i == -1) throw new Error("should never happen");
                    parent.activeChildren.setElementAt(this,i);
                }
            } else {
                int newpid = -1;
                int nextPID = gs.nextPID;
                for(int i=nextPID; i<tasks.length; i++) if(tasks[i] == null) {
                        newpid = i;
                        break;
                    }
                if(newpid == -1) for(int i=1; i<nextPID; i++) if(tasks[i] == null) {
                            newpid = i;
                            break;
                        }
                if(newpid == -1) throw new ProcessTableFullExn();
                pid = newpid;
                gs.nextPID = newpid + 1;
            }
            tasks[pid] = this;
        }
    }
    
    protected int _syscall(int syscall, int a, int b, int c, int d, int e, int f) throws ErrnoException, FaultException {
        switch(syscall) {
	case SYS_kill:
             return sys_kill(a,b);
         case SYS_fork:
             return sys_fork();
         case SYS_pipe:
             return sys_pipe(a);
         case SYS_dup2:
             return sys_dup2(a,b);
         case SYS_dup:
             return sys_dup(a);
         case SYS_waitpid:
             return sys_waitpid(a,b,c);
         case SYS_stat:
             return sys_stat(a,b);
         case SYS_lstat:
             return sys_lstat(a,b);
         case SYS_mkdir:
             return sys_mkdir(a,b);
         case SYS_getcwd:
             return sys_getcwd(a,b);
         case SYS_chdir:
             return sys_chdir(a);
         case SYS_exec:
             return sys_exec(a,b,c);
         case SYS_getdents:
             return sys_getdents(a,b,c,d);
         case SYS_unlink:
             return sys_unlink(a);
        case SYS_link:
            return sys_link(a,b);
         case SYS_getppid:
             return sys_getppid();
         case SYS_socket:
             return sys_socket(a,b,c);
         case SYS_connect:
             return sys_connect(a,b,c);
         case SYS_resolve_hostname:
             return sys_resolve_hostname(a,b,c);
         case SYS_setsockopt:
             return sys_setsockopt(a,b,c,d,e);
         case SYS_getsockopt:
             return sys_getsockopt(a,b,c,d,e);
         case SYS_getsockname:
             return sys_getsockname(a,b,c);
         case SYS_getpeername:
             return sys_getpeername(a,b,c);
         case SYS_bind:
             return sys_bind(a,b,c);
         case SYS_listen:
             return sys_listen(a,b);
         case SYS_accept:
             return sys_accept(a,b,c);
         case SYS_shutdown:
             return sys_shutdown(a,b);
         case SYS_sysctl:
             return sys_sysctl(a,b,c,d,e,f);
         case SYS_sendto:
             return sys_sendto(a,b,c,d,e,f);
         case SYS_recvfrom:
             return sys_recvfrom(a,b,c,d,e,f);
         case SYS_select:
             return sys_select(a,b,c,d,e);
         case SYS_access:
             return sys_access(a,b);
         case SYS_realpath:
             return sys_realpath(a,b);
         case SYS_chown:
             return sys_chown(a,b,c);
         case SYS_lchown:
             return sys_chown(a,b,c);
         case SYS_fchown:
             return sys_fchown(a,b,c);
         case SYS_chmod:
             return sys_chmod(a,b,c);
         case SYS_fchmod:
             return sys_fchmod(a,b,c);
         case SYS_fcntl:
             return sys_fcntl_lock(a,b,c);
         case SYS_umask:
             return sys_umask(a);
         case SYS_resolve_ip:
             return sys_resolve_ip(a,b,c);
         case SYS_rmdir:
             return sys_rmdir(a);
         case SYS_setsid:
             return sys_setsid();
 
         default:
             return super._syscall(syscall,a,b,c,d,e,f);
         }
    }

    
    int sys_resolve_ip(int addr, int ptrCBuf, int size) throws FaultException {
	byte[] buffer = new byte[4];
	//java.util.logging.Logger.getLogger(getClass().getName()).info("Input address = " + Integer.toString(addr));
	buffer[0] = Integer.valueOf((addr >>> 24) & 0xff).byteValue();
	buffer[1] = Integer.valueOf((addr >>> 16) & 0xff).byteValue();
	buffer[2] = Integer.valueOf((addr >>>  8) & 0xff).byteValue();
	buffer[3] = Integer.valueOf((addr >>>  0) & 0xff).byteValue();

	String hostName;
	try {
	   hostName = InetAddress.getByAddress(buffer).getHostAddress();
	} catch (UnknownHostException ex) {
	   hostName = java.text.MessageFormat.format("{0}.{1}.{2}.{3}", new Object[] { 
		   Integer.valueOf(buffer[0] & 0xff), 
		    		   Integer.valueOf(buffer[1] & 0xff), 
		    			           Integer.valueOf(buffer[2] & 0xff ), 
		    					           Integer.valueOf( buffer[3] & 0xff )} );
	}
	//java.util.logging.Logger.getLogger(getClass().getName()).info("Output address = " + hostName);
	byte[] outBytes = getBytes(hostName);
	if(size == 0) return -EINVAL;
	if(size < outBytes.length+1) return -ERANGE;
	copyout(outBytes,ptrCBuf,outBytes.length);
	memset(ptrCBuf+outBytes.length+1,0,1);
	
	return 0;
    }

    FD _open(String path, int flags, int mode) throws ErrnoException {
        path = normalizePath(path);
        FD fd = gs.open(this,path,flags,mode);
        if (fd != null && path != null) fd.setNormalizedPath(path);
        return fd;
    }

    private int sys_getppid() {
        return parent == null ? 1 : parent.pid;
    }

    private int sys_setsid() {
	return 0;
    }

    private int sys_chown(int fileAddr, int uid, int gid) {
        return 0;
    }

    private int sys_lchown(int fileAddr, int uid, int gid) {
        return 0;
    }

    private int sys_fchown(int fd, int uid, int gid) {
        return 0;
    }

    private int sys_chmod(int fileAddr, int uid, int gid) {
        return 0;
    }

    private int sys_fchmod(int fd, int uid, int gid) {
        return 0;
    }

    private int sys_umask(int mask) {
        return 0;
    }

    private int sys_access(int cstring, int mode) throws ErrnoException, ReadFaultException {
        // FEATURE: sys_access
        return gs.stat(this,normalizePath(cstring(cstring))) == null ? -ENOENT : 0;
    }

    private int sys_realpath(int inAddr, int outAddr) throws FaultException {
        String s = normalizePath(cstring(inAddr));
        byte[] b = getNullTerminatedBytes(s);
        if(b.length > PATH_MAX) return -ERANGE;
        copyout(b,outAddr,b.length);
        return 0;
    }

    // FEATURE: Signal handling
    // check flag only on backwards jumps to basic blocks without compulsatory checks
    // (see A Portable Research Framework for the Execution of Java Bytecode - Etienne Gagnon, Chapter 2)

    /** The kill syscall.
       SIGSTOP, SIGTSTO, SIGTTIN, and SIGTTOUT pause the process.
       SIGCONT, SIGCHLD, SIGIO, and SIGWINCH are ignored.
       Anything else terminates the process. */
    private int sys_kill(int pid, int signal) {
        // This will only be called by raise() in newlib to invoke the default handler
        // We don't have to worry about actually delivering the signal
        if(pid != pid) return -ESRCH;
        if(signal < 0 || signal >= 32) return -EINVAL;
        switch(signal) {
        case 0:
            return 0;
        case 17: // SIGSTOP
        case 18: // SIGTSTP
        case 21: // SIGTTIN
        case 22: // SIGTTOU
        case 19: // SIGCONT
        case 20: // SIGCHLD
        case 23: // SIGIO
        case 28: // SIGWINCH
            break;
        default:
            exit(128+signal, true);
        }
        return 0;
    }

    private int sys_waitpid(int pid, int statusAddr, int options) throws FaultException, ErrnoException {
        final int WNOHANG = 1;
        if((options & ~(WNOHANG)) != 0) return -EINVAL;
        if(pid == 0 || pid < -1) {
            if(STDERR_DIAG) System.err.println("WARNING: waitpid called with a pid of " + pid);
            return -ECHILD;
        }
        boolean blocking = (options&WNOHANG)==0;

        if(pid !=-1 && (pid <= 0 || pid >= gs.tasks.length)) return -ECHILD;
        if(children == null) return blocking ? -ECHILD : 0;

        UnixRuntime done = null;

        synchronized(children) {
            for(;;) {
                if(pid == -1) {
                    if(exitedChildren.size() > 0) {
                        done = (UnixRuntime)exitedChildren.elementAt(exitedChildren.size() - 1);
                        exitedChildren.removeElementAt(exitedChildren.size() - 1);
                    }
                } else if(pid > 0) {
                    if(pid >= gs.tasks.length) return -ECHILD;
                    UnixRuntime t = gs.tasks[pid];
                    if(t.parent != this) return -ECHILD;
                    if(t.state == EXITED) {
                        if(!exitedChildren.removeElement(t)) throw new Error("should never happen");
                        done = t;
                    }
                } else {
                    // process group stuff, EINVAL returned above
                    throw new Error("should never happen");
                }
                if(done == null) {
                    if(!blocking) return 0;
                    try {
                        children.wait();
                    }
                    catch(InterruptedException e) {}
                    //System.err.println("waitpid woke up: " + exitedChildren.size());
                } else {
                    gs.tasks[done.pid] = null;
                    break;
                }
            }
        }
        if(statusAddr!=0) memWrite(statusAddr,done.exitStatus()<<8);
        return done.pid;
    }


    void _exited() {
        if(children != null) synchronized(children) {
                for(Enumeration e = exitedChildren.elements(); e.hasMoreElements(); ) {
                    UnixRuntime child = (UnixRuntime) e.nextElement();
                    gs.tasks[child.pid] = null;
                }
                exitedChildren.removeAllElements();
                for(Enumeration e = activeChildren.elements(); e.hasMoreElements(); ) {
                    UnixRuntime child = (UnixRuntime) e.nextElement();
                    child.parent = null;
                }
                activeChildren.removeAllElements();
            }

        UnixRuntime _parent = parent;
        if(_parent == null) {
            gs.tasks[pid] = null;
        } else {
            synchronized(_parent.children) {
                if(parent == null) {
                    gs.tasks[pid] = null;
                } else {
                    if(!parent.activeChildren.removeElement(this)) throw new Error("should never happen _exited: pid: " + pid);
                    parent.exitedChildren.addElement(this);
                    parent.children.notify();
                }
            }
        }
    }

    protected Object clone() throws CloneNotSupportedException {
        UnixRuntime r = (UnixRuntime) super.clone();
        r.pid = 0;
        r.parent = null;
        r.children = null;
        r.activeChildren = r.exitedChildren = null;
        return r;
    }

    private int sys_fork() {
        final UnixRuntime r;

        try {
            r = (UnixRuntime) clone();
        } catch(Exception e) {
            e.printStackTrace();
            return -ENOMEM;
        }

        r.parent = this;

        try {
            r._started();
        } catch(ProcessTableFullExn e) {
            return -ENOMEM;
        }

        //System.err.println("fork " + pid + " -> " + r.pid + " tasks[" + r.pid + "] = " + gd.tasks[r.pid]);
        if(children == null) {
            children = new Object();
            activeChildren = new Vector<UnixRuntime>();
            exitedChildren = new Vector<UnixRuntime>();
        }
        activeChildren.addElement(r);

        CPUState state = new CPUState();
        getCPUState(state);
        state.r[V0] = 0; // return 0 to child
        state.pc += 4; // skip over syscall instruction
        r.setCPUState(state);
        r.state = PAUSED;

        new ForkedProcess(r);

        return r.pid;
    }

    public static final class ForkedProcess extends Thread {
        private final UnixRuntime initial;
        public ForkedProcess(UnixRuntime initial) {
            this.initial = initial;
            start();
        }
        public void run() {
            UnixRuntime.executeAndExec(initial);
        }
    }

    public static int runAndExec(UnixRuntime r, String argv0, String[] rest) {
        return runAndExec(r,concatArgv(argv0,rest));
    }
    public static int runAndExec(UnixRuntime r, String[] argv) {
        r.start(argv);
        return executeAndExec(r);
    }

    public static int executeAndExec(UnixRuntime r) {
        for(;;) {
            for(;;) {
                if(r.execute()) break;
                if(STDERR_DIAG) System.err.println("WARNING: Pause requested while executing runAndExec()");
            }
            if(r.state != EXECED) return r.exitStatus();
            r = r.execedRuntime;
        }
    }

    private String[] readStringArray(int addr) throws ReadFaultException {
        int count = 0;
        for(int p=addr; memRead(p) != 0; p+=4) count++;
        String[] a = new String[count];
        for(int i=0,p=addr; i<count; i++,p+=4) a[i] = cstring(memRead(p));
        return a;
    }

    private int sys_exec(int cpath, int cargv, int cenvp) throws ErrnoException, FaultException {
        return exec(normalizePath(cstring(cpath)),readStringArray(cargv),readStringArray(cenvp));
    }

    private final static Method runtimeCompilerCompile;
    static {
        Method m;
        try {
            m = Class.forName("org.ibex.nestedvm.RuntimeCompiler").getMethod("compile",new Class[] {Seekable.class,String.class,String.class});
        } catch(NoSuchMethodException e) {
            m = null;
        } catch(ClassNotFoundException e) {
            m = null;
        }
        runtimeCompilerCompile = m;
    }

    public Class runtimeCompile(Seekable s, String sourceName) throws IOException {
        if(runtimeCompilerCompile == null) {
            if(STDERR_DIAG) System.err.println("WARNING: Exec attempted but RuntimeCompiler not found!");
            return null;
        }

        try {
            return (Class) runtimeCompilerCompile.invoke(null,new Object[] {s,"unixruntime,maxinsnpermethod=256,lessconstants",sourceName});
        } catch(IllegalAccessException e) {
            e.printStackTrace();
            return null;
        } catch(InvocationTargetException e) {
            Throwable t = e.getTargetException();
            if(t instanceof IOException) throw (IOException) t;
            if(t instanceof RuntimeException) throw (RuntimeException) t;
            if(t instanceof Error) throw (Error) t;
            if(STDERR_DIAG) t.printStackTrace();
            return null;
        }
    }

    private int exec(String path, String[] argv, String[] envp) throws ErrnoException {
        if(argv.length == 0) argv = new String[] {""};
        // HACK: Hideous hack to make a standalone busybox possible
        if(path.equals("bin/busybox") && getClass().getName().endsWith("BusyBox"))
            return execClass(getClass(),argv,envp);

        // NOTE: For this little hack to work nestedvm.root MUST be "."
        /*try {
            System.err.println("Execing normalized path: " + normalizedPath);
            if(true) return exec(new Interpreter(normalizedPath),argv,envp);
        } catch(IOException e) { throw new Error(e); }*/

        FStat fstat = gs.stat(this,path);
        if(fstat == null) return -ENOENT;
        GlobalState.CacheEnt ent = (GlobalState.CacheEnt) gs.execCache.get(path);
        long mtime = fstat.mtime();
        long size = fstat.size();
        if(ent != null) {
            //System.err.println("Found cached entry for " + path);
            if(ent.time ==mtime && ent.size == size) {
                if(ent.o instanceof Class)
                    return execClass((Class) ent.o,argv,envp);
                if(ent.o instanceof String[])
                    return execScript(path,(String[]) ent.o,argv,envp);
                throw new Error("should never happen");
            }
            //System.err.println("Cache was out of date");
            gs.execCache.remove(path);
        }

        FD fd = gs.open(this,path,RD_ONLY,0);
        if(fd == null) throw new ErrnoException(ENOENT);
        Seekable s = fd.seekable();
        if(s == null) throw new ErrnoException(EACCES);

        byte[] buf = new byte[4096];

        try {
            int n = s.read(buf,0,buf.length);
            if(n == -1) throw new ErrnoException(ENOEXEC);

            switch(buf[0]) {
            case '\177': // possible ELF
                if(n < 4) s.tryReadFully(buf,n,4-n);
                if(buf[1] != 'E' || buf[2] != 'L' || buf[3] != 'F') return -ENOEXEC;
                s.seek(0);
                if(STDERR_DIAG) System.err.println("Running RuntimeCompiler for " + path);
                Class c = runtimeCompile(s,path);
                if(STDERR_DIAG) System.err.println("RuntimeCompiler finished for " + path);
                if(c == null) throw new ErrnoException(ENOEXEC);
                gs.execCache.put(path,new GlobalState.CacheEnt(mtime,size,c));
                return execClass(c,argv,envp);
            case '#':
                if(n == 1) {
                    int n2 = s.read(buf,1,buf.length-1);
                    if(n2 == -1) return -ENOEXEC;
                    n += n2;
                }
                if(buf[1] != '!') return -ENOEXEC;
                int p = 2;
                n -= 2;
                OUTER: for(;;) {
                    for(int i=p; i<p+n; i++) if(buf[i] == '\n') {
                            p = i;
                            break OUTER;
                        }
                    p += n;
                    if(p == buf.length) break OUTER;
                    n = s.read(buf,p,buf.length-p);
                }
                int cmdStart = 2;
                for(; cmdStart<p; cmdStart++) if(buf[cmdStart]!=' ') break;
                if(cmdStart == p) throw new ErrnoException(ENOEXEC);
                int argStart = cmdStart;
                for(; argStart<p; argStart++) if(buf[argStart] == ' ') break;
                int cmdEnd = argStart;
                while(argStart < p && buf[argStart] == ' ') argStart++;
                String[] command = new String[] {
                    new String(buf,cmdStart,cmdEnd-cmdStart),
                    argStart < p ? new String(buf,argStart,p-argStart) : null
                };
                gs.execCache.put(path,new GlobalState.CacheEnt(mtime,size,command));
                return execScript(path,command,argv,envp);
            default:
                return -ENOEXEC;
            }
        } catch(IOException e) {
            return -EIO;
        } finally {
            fd.close();
        }
    }

    public int execScript(String path, String[] command, String[] argv, String[] envp) throws ErrnoException {
        String[] newArgv = new String[argv.length-1 + (command[1] != null ? 3 : 2)];
        int p = command[0].lastIndexOf('/');
        newArgv[0] = p == -1 ? command[0] : command[0].substring(p+1);
        newArgv[1] = "/" + path;
        p = 2;
        if(command[1] != null) newArgv[p++] = command[1];
        for(int i=1; i<argv.length; i++) newArgv[p++] = argv[i];
        if(p != newArgv.length) throw new Error("p != newArgv.length");
        System.err.println("Execing: " + command[0]);
        for(int i=0; i<newArgv.length; i++) System.err.println("execing [" + i + "] " + newArgv[i]);
        return exec(command[0],newArgv,envp);
    }

    public int execClass(Class<?> c,String[] argv, String[] envp) {
        try {
            UnixRuntime r = (UnixRuntime) c.getDeclaredConstructor(new Class[] {Boolean.TYPE}).newInstance(new Object[] {Boolean.TRUE});
            return exec(r,argv,envp);
        } catch(Exception e) {
            e.printStackTrace();
            return -ENOEXEC;
        }
    }

    private int exec(UnixRuntime r, String[] argv, String[] envp) {     
        //System.err.println("Execing " + r);
        for(int i=0; i<OPEN_MAX; i++) if(closeOnExec[i]) closeFD(i);
        r.fds = fds;
        r.closeOnExec = closeOnExec;
        // make sure this doesn't get messed with these since we didn't copy them
        fds = null;
        closeOnExec = null;

	r.gs = gs;
        r.sm = sm;
        r.cwd = cwd;
        r.pid = pid;
        r.parent = parent;
        r.start(argv,envp);

	state = EXECED;
        execedRuntime = r;

	return 0;   
    }

    public static class Pipe {
        private final byte[] pipebuf = new byte[PIPE_BUF*4];
        private int readPos;
        private int writePos;

	public final FD reader = new Reader();
        public final FD writer = new Writer();

	public class Reader extends FD {
            protected FStat _fstat() { return new SocketFStat(); }
            public int read(byte[] buf, int off, int len) throws ErrnoException {
                if(len == 0) return 0;
                synchronized(Pipe.this) {
                    while(writePos != -1 && readPos == writePos) {
                        try { 
			   Pipe.this.wait(); 
			} catch(InterruptedException e) { 
			   /* ignore */ 
			}
                    }
                    if(writePos == -1) return 0; // eof
                    len = Math.min(len,writePos-readPos);
                    System.arraycopy(pipebuf,readPos,buf,off,len);
                    readPos += len;
                    if(readPos == writePos) Pipe.this.notify();
                    return len;
                }
            }
            public int flags() {
                return O_RDONLY;
            }
            public void _close() {
                synchronized(Pipe.this) {
                    readPos = -1;
                    Pipe.this.notify();
                }
            }
        }

        public class Writer extends FD {
            protected FStat _fstat() {
                return new SocketFStat();
            }
            public int write(byte[] buf, int off, int len) throws ErrnoException {
                if(len == 0) return 0;
                synchronized(Pipe.this) {
                    if(readPos == -1) throw new ErrnoException(EPIPE);
                    if(pipebuf.length - writePos < Math.min(len,PIPE_BUF)) {
                        // not enough space to atomicly write the data
                        while(readPos != -1 && readPos != writePos) {
                            try {
                                Pipe.this.wait();
                            }
                            catch(InterruptedException e) {
                                /* ignore */
                            }
                        }
                        if(readPos == -1) throw new ErrnoException(EPIPE);
                        readPos = writePos = 0;
                    }
                    len = Math.min(len,pipebuf.length - writePos);
                    System.arraycopy(buf,off,pipebuf,writePos,len);
                    if(readPos == writePos) Pipe.this.notify();
                    writePos += len;
                    return len;
                }
            }
            public int flags() {
                return O_WRONLY;
            }
            public void _close() {
                synchronized(Pipe.this) {
                    writePos = -1;
                    Pipe.this.notify();
                }
            }
        }
    }

    private int sys_pipe(int addr) {
        Pipe pipe = new Pipe();

        int fd1 = addFD(pipe.reader);
        if(fd1 < 0) return -ENFILE;
        int fd2 = addFD(pipe.writer);
        if(fd2 < 0) {
            closeFD(fd1);
            return -ENFILE;
        }

        try {
            memWrite(addr,fd1);
            memWrite(addr+4,fd2);
        } catch(FaultException e) {
            closeFD(fd1);
            closeFD(fd2);
            return -EFAULT;
        }
        return 0;
    }

    private int sys_dup2(int oldd, int newd) {
        if(oldd == newd) return 0;
        if(oldd < 0 || oldd >= OPEN_MAX) return -EBADFD;
        if(newd < 0 || newd >= OPEN_MAX) return -EBADFD;
        if(fds[oldd] == null) return -EBADFD;
        if(fds[newd] != null) fds[newd].close();
        fds[newd] = fds[oldd].dup();
        return 0;
    }

    private int sys_dup(int oldd) {
        if(oldd < 0 || oldd >= OPEN_MAX) return -EBADFD;
        if(fds[oldd] == null) return -EBADFD;
        FD fd = fds[oldd].dup();
        int newd = addFD(fd);
        if(newd < 0) {
            fd.close();
            return -ENFILE;
        }
        return newd;
    }

    private int sys_stat(int cstring, int addr) throws FaultException, ErrnoException {
        FStat s = gs.stat(this,normalizePath(cstring(cstring)));
        if(s == null) return -ENOENT;
        return stat(s,addr);
    }

    private int sys_lstat(int cstring, int addr) throws FaultException, ErrnoException {
        FStat s = gs.lstat(this,normalizePath(cstring(cstring)));
        if(s == null) return -ENOENT;
        return stat(s,addr);
    }

    private int sys_mkdir(int cstring, int mode) throws FaultException, ErrnoException {
        gs.mkdir(this,normalizePath(cstring(cstring)),mode);
        return 0;
    }

    private int sys_unlink(int cstring) throws FaultException, ErrnoException {
        gs.unlink(this,normalizePath(cstring(cstring)));
        return 0;
    }

    private int sys_link(int cstring1, int cstring2) throws FaultException, ErrnoException {
        gs.link(this,normalizePath(cstring(cstring1)), normalizePath(cstring(cstring2)));
        return 0;
    }

    private int sys_rmdir(int ptrPathName) throws ErrnoException, ReadFaultException {
        gs.rmdir(this, normalizePath(cstring(ptrPathName)));
        return 0;
    }

    private int sys_getcwd(int addr, int size) throws FaultException, ErrnoException {
        byte[] b = getBytes(cwd);
        if(size == 0) return -EINVAL;
        if(size < b.length+2) return -ERANGE;
        memset(addr,'/',1);
        copyout(b,addr+1,b.length);
        memset(addr+b.length+1,0,1);
        return addr;
    }

    private int sys_chdir(int addr) throws ErrnoException, FaultException {
        String path = normalizePath(cstring(addr));
        FStat st = gs.stat(this,path);
        if(st == null) return -ENOENT;
        if(st.type() != FStat.S_IFDIR) return -ENOTDIR;
        cwd = path;
        return 0;
    }

    private int sys_getdents(int fdn, int addr, int count, int seekptr) throws FaultException, ErrnoException {
        count = Math.min(count,MAX_CHUNK);
        if(fdn < 0 || fdn >= OPEN_MAX) return -EBADFD;
        if(fds[fdn] == null) return -EBADFD;
        byte[] buf = byteBuf(count);
        int n = fds[fdn].getdents(buf,0,count);
        copyout(buf,addr,n);
        return n;
    }

    void _preCloseFD(FD fd) {
        // release all fcntl locks on this file
        Seekable s = fd.seekable();
        if (s == null) return;

        try {
            for (int i=0; i < gs.locks.length; i++) {
                Seekable.Lock l = gs.locks[i];
                if (l == null) continue;
                if (s.equals(l.seekable()) && l.getOwner() == this) {
                    l.release();
                    gs.locks[i] = null;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void _postCloseFD(FD fd) {
        if (fd.isMarkedForDeleteOnClose()) {
            try {
                gs.unlink(this, fd.getNormalizedPath());
            }
            catch (Throwable t) {}
        }
    }

    /** Implements the F_GETLK and F_SETLK cases of fcntl syscall.
     *  If l_start = 0 and l_len = 0 the lock refers to the entire file.
     *  Uses GlobalState to ensure locking across processes in the same JVM.
     struct flock {
       short   l_type;         // lock type: F_UNLCK, F_RDLCK, F_WRLCK
       short   l_whence;       // type of l_start: SEEK_SET, SEEK_CUR, SEEK_END
       long    l_start;        // starting offset, bytes
       long    l_len;          // len = 0 means until EOF
       short   l_pid;          // lock owner
       short   l_xxx;          // padding
     };
     */
    private int sys_fcntl_lock(int fdn, int cmd, int arg) throws FaultException {
        if (cmd != F_GETLK && cmd != F_SETLK) return sys_fcntl(fdn, cmd, arg);

        if(fdn < 0 || fdn >= OPEN_MAX) return -EBADFD;
        if(fds[fdn] == null) return -EBADFD;
        FD fd = fds[fdn];

        if (arg == 0) return -EINVAL;
        int word     = memRead(arg);
        int l_start  = memRead(arg+4);
        int l_len    = memRead(arg+8);
        int l_type   = word>>16;
        int l_whence = word&0x00ff;

        Seekable.Lock[] locks = gs.locks;
        Seekable s = fd.seekable();
        if (s == null) return -EINVAL;

        try {

            switch (l_whence) {
            case SEEK_SET:
                break;
            case SEEK_CUR:
                l_start += s.pos();
                break;
            case SEEK_END:
                l_start += s.length();
                break;
            default:
                return -1;
            }

            if (cmd == F_GETLK) {
                // The simple Java file locking below will happily return
                // a lock that overlaps one already held by the JVM. Thus
                // we must check over all the locks held by other Runtimes
                for (int i=0; i < locks.length; i++) {
                    if (locks[i] == null || !s.equals(locks[i].seekable()))
                        continue;
                    if (!locks[i].overlaps(l_start, l_len))
                        continue;
                    if (locks[i].getOwner() == this)
                        continue;
                    if (locks[i].isShared() && l_type == F_RDLCK)
                        continue;

                    // overlapping lock held by another process
                    return 0;
                }

                // check if an area is lockable by attempting to obtain a lock
                Seekable.Lock lock = s.lock(l_start, l_len, l_type == F_RDLCK);

                if (lock != null) { // no lock exists
                    memWrite(arg, SEEK_SET|(F_UNLCK<<16));
                    lock.release();
                }

                return 0;
            }

            // now processing F_SETLK
            if (cmd != F_SETLK) return -EINVAL;

            if (l_type == F_UNLCK) {
                // release all locks that fall within the boundaries given
                for (int i=0; i < locks.length; i++) {
                    if (locks[i] == null || !s.equals(locks[i].seekable()))
                        continue;
                    if (locks[i].getOwner() != this) continue;

                    int pos = (int)locks[i].position();
                    if (pos < l_start) continue;
                    if (l_start != 0 && l_len != 0) // start/len 0 means unlock all
                        if (pos + locks[i].size() > l_start + l_len)
                            continue;

                    locks[i].release();
                    locks[i] = null;
                }
                return 0;

            } else if (l_type == F_RDLCK || l_type == F_WRLCK) {
                // first see if a lock already exists
                for (int i=0; i < locks.length; i++) {
                    if (locks[i] == null || !s.equals(locks[i].seekable()))
                        continue;

                    if (locks[i].getOwner() == this) {
                        // if this Runtime owns an overlapping lock work with it
                        if (locks[i].contained(l_start, l_len)) {
                            locks[i].release();
                            locks[i] = null;
                        } else if (locks[i].contains(l_start, l_len)) {
                            if (locks[i].isShared() == (l_type == F_RDLCK)) {
                                // return this more general lock
                                memWrite(arg+4, (int)locks[i].position());
                                memWrite(arg+8, (int)locks[i].size());
                                return 0;
                            } else {
                                locks[i].release();
                                locks[i] = null;
                            }
                        }
                    } else {
                        // if another Runtime has an lock and it is exclusive or
                        // we want an exclusive lock then fail
                        if (locks[i].overlaps(l_start, l_len)
                                && (!locks[i].isShared() || l_type == F_WRLCK))
                            return -EAGAIN;
                    }
                }

                // create the lock
                Seekable.Lock lock = s.lock(l_start, l_len, l_type == F_RDLCK);
                if (lock == null) return -EAGAIN;
                lock.setOwner(this);

                int i;
                for (i=0; i < locks.length; i++)
                    if (locks[i] == null) break;
                if (i == locks.length) return -ENOLCK;
                locks[i] = lock;
                return 0;

            } else {
                return -EINVAL;
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class SocketFD extends FD {
        public static final int TYPE_STREAM = 0;
        public static final int TYPE_DGRAM = 1;
        public static final int LISTEN = 2;
        public int type() {
            return flags & 1;
        }
        public boolean listen() {
            return (flags & 2) != 0;
        }

        int flags;
        int options;

        Socket s;
        ServerSocket ss;
        DatagramSocket ds;

        InetAddress bindAddr;
        int bindPort = -1;
        InetAddress connectAddr;
        int connectPort = -1;

        DatagramPacket dp;
        InputStream is;
        OutputStream os;

        private static final byte[] EMPTY = new byte[0];
        public SocketFD(int type) {
            flags = type;
            if(type == TYPE_DGRAM)
                dp = new DatagramPacket(EMPTY,0);
        }

        public SocketAddress getLocalSocketAddress() {
            int type = type();
            if(type == SocketFD.TYPE_STREAM) {
                if(listen()) {
                    return ss.getLocalSocketAddress();
                } else {
                    return s.getLocalSocketAddress();
                }
            } else {
                return ds.getLocalSocketAddress();
            }
        }

        public void setOptions() {
            try {
                if(s != null && type() == TYPE_STREAM && !listen()) {
                    Platform.socketSetKeepAlive(s,(options & SO_KEEPALIVE) != 0);
                }
            } catch(SocketException e) {
                if(STDERR_DIAG) e.printStackTrace();
            }
        }

        public void _close() {
            try {
                if(s != null) s.close();
                if(ss != null) ss.close();
                if(ds != null) ds.close();
            } catch(IOException e) {
                /* ignore */
            }
        }

        public int read(byte[] a, int off, int length) throws ErrnoException {
            if(type() == TYPE_DGRAM) return recvfrom(a,off,length,null,null);
            if(is == null) throw new ErrnoException(EPIPE);
            try {
                int n = is.read(a,off,length);
                return n < 0 ? 0 : n;
            } catch(IOException e) {
                throw new ErrnoException(EIO);
            }
        }

        public int recvfrom(byte[] a, int off, int length, InetAddress[] sockAddr, int[] port) throws ErrnoException {
            if(type() == TYPE_STREAM) return read(a,off,length);

            if(off != 0) throw new IllegalArgumentException("off must be 0");
            dp.setData(a);
            dp.setLength(length);
            try {
                if(ds == null) ds = new DatagramSocket();
                ds.receive(dp);
            } catch(IOException e) {
                if(STDERR_DIAG) e.printStackTrace();
                throw new ErrnoException(EIO);
            }
            if(sockAddr != null) {
                sockAddr[0] = dp.getAddress();
                port[0] = dp.getPort();
            }
            return dp.getLength();
        }

        public int write(byte[] a, int off, int length) throws ErrnoException {
            if(type() == TYPE_DGRAM) return  sendto(a,off,length,null,-1);

            if(os == null) throw new ErrnoException(EPIPE);
            try {
                os.write(a,off,length);
                return length;
            } catch(IOException e) {
                throw new ErrnoException(EIO);
            }
        }

        public int sendto(byte[] a, int off, int length, InetAddress destAddr, int destPort) throws ErrnoException {
            if(off != 0) throw new IllegalArgumentException("off must be 0");
            if(type() == TYPE_STREAM) return write(a,off,length);

            if(destAddr == null) {
                destAddr = connectAddr;
                destPort = connectPort;

                if(destAddr == null) throw new ErrnoException(ENOTCONN);
            }

            dp.setAddress(destAddr);
            dp.setPort(destPort);
            dp.setData(a);
            dp.setLength(length);

            try {
                if(ds == null) ds = new DatagramSocket();
                ds.send(dp);
            } catch(IOException e) {
                if(STDERR_DIAG) e.printStackTrace();
                if("Network is unreachable".equals(e.getMessage())) throw new ErrnoException(EHOSTUNREACH);
                throw new ErrnoException(EIO);
            }
            return dp.getLength();
        }

        public int flags() {
            return O_RDWR;
        }
        public FStat _fstat() {
            return new SocketFStat();
        }
    }

    private int sys_socket(int domain, int type, int proto) {
        if(domain != AF_INET || (type != SOCK_STREAM && type != SOCK_DGRAM)) return -EPROTONOSUPPORT;
        return addFD(new SocketFD(type == SOCK_STREAM ? SocketFD.TYPE_STREAM : SocketFD.TYPE_DGRAM));
    }

    private SocketFD getSocketFD(int fdn) throws ErrnoException {
        if(fdn < 0 || fdn >= OPEN_MAX) throw new ErrnoException(EBADFD);
        if(fds[fdn] == null) throw new ErrnoException(EBADFD);
        if(!(fds[fdn] instanceof SocketFD)) throw new ErrnoException(ENOTSOCK);

        return (SocketFD) fds[fdn];
    }

    private int sys_connect(int fdn, int addr, int namelen) throws ErrnoException, FaultException {
        SocketFD fd = getSocketFD(fdn);

        if(fd.type() == SocketFD.TYPE_STREAM && (fd.s != null || fd.ss != null)) return -EISCONN;
        int word1 = memRead(addr);
        if( ((word1 >>> 16)&0xff) != AF_INET) return -EAFNOSUPPORT;
        int port = word1 & 0xffff;
        byte[] ip = new byte[4];
        copyin(addr+4,ip,4);

        InetAddress inetAddr;
        try {
            inetAddr = Platform.inetAddressFromBytes(ip);
        } catch(UnknownHostException e) {
            return -EADDRNOTAVAIL;
        }

        fd.connectAddr = inetAddr;
        fd.connectPort = port;

        try {
            switch(fd.type()) {
            case SocketFD.TYPE_STREAM: {
                Socket s = new Socket(inetAddr,port);
                fd.s = s;
                fd.setOptions();
                fd.is = s.getInputStream();
                fd.os = s.getOutputStream();
                break;
            }
            case SocketFD.TYPE_DGRAM:
                break;
            default:
                throw new Error("should never happen");
            }
        } catch(IOException e) {
            return -ECONNREFUSED;
        }

        return 0;
    }

    private int sys_resolve_hostname(int chostname, int addr, int sizeAddr) throws FaultException {
        String hostname = cstring(chostname);
        int size = memRead(sizeAddr);
        InetAddress[] inetAddrs;
        try {
            inetAddrs = InetAddress.getAllByName(hostname);
        } catch(UnknownHostException e) {
            return HOST_NOT_FOUND;
        }
        int count = min(size/4,inetAddrs.length);
        for(int i=0; i<count; i++,addr+=4) {
            byte[] b = inetAddrs[i].getAddress();
            copyout(b,addr,4);
        }
        memWrite(sizeAddr,count*4);
        return 0;
    }

    private int sys_setsockopt(int fdn, int level, int name, int valaddr, int len) throws ReadFaultException, ErrnoException {
        SocketFD fd = getSocketFD(fdn);
        switch(level) {
           case SOL_SOCKET:
              switch(name) {
                 case SO_REUSEADDR:
                 case SO_KEEPALIVE: {
                    if(len != 4) return -EINVAL;
                    int val = memRead(valaddr);
                    if(val != 0) fd.options |= name;
                    else fd.options &= ~name;
                    fd.setOptions();
                    return 0;
                 }
                 default:
                    if(STDERR_DIAG) System.err.println("Unknown setsockopt name passed: " + name);
                    return -ENOPROTOOPT;
              }
           default:
              if(STDERR_DIAG) System.err.println("Unknown setsockopt leve passed: " + level);
              return -ENOPROTOOPT;
        }                   
    }

    private int sys_getpeername(int socketDescriptor, int ptrSockName, int ptrNameLength) throws FaultException, ErrnoException
    {
       SocketFD fd = getSocketFD(socketDescriptor);
       InetSocketAddress isa = (InetSocketAddress) fd.s.getRemoteSocketAddress();
       return getfooonamehelper(isa, ptrSockName, ptrNameLength);
    }

    private int sys_getsockname(int socketDescriptor, int ptrSockName, int ptrNameLength) throws FaultException, ErrnoException
    {
       SocketFD fd = getSocketFD(socketDescriptor);
       InetSocketAddress isa = (InetSocketAddress) fd.getLocalSocketAddress();
       return getfooonamehelper(isa, ptrSockName, ptrNameLength);
    }


    private int getfooonamehelper(InetSocketAddress isa, int ptrSockName, int ptrNameLength) throws FaultException, ErrnoException
    {
       int length = 8;
       int bufferSize = memRead(ptrNameLength);
       byte[] ia = isa.getAddress().getAddress();
       int port = isa.getPort();
    
       /*
       byte sin_len
       byte sin_family
       short sin_port
       int sin_addr
       */
       int dword1 = ((length & 0xff) << 24) | ((AF_INET & 0xff) << 16) | ( (port & 0xffff) << 0);
       int dword2 = (ia[0] << 24) | (ia[1] << 16) | (ia[2] << 8) | (ia[3] << 0);
       memWrite(ptrNameLength, length);
       if (bufferSize == 0) {
          return 0;
       }
       if (bufferSize < 4) {
          int oldDword1 = memRead(ptrSockName);
          int lowermask = (1 << bufferSize * 8) - 1;
          int highermask = -1 - lowermask;
          dword1 = (oldDword1 & highermask) | (dword1 & lowermask);
       }
       memWrite(ptrSockName, dword1);
       if (bufferSize < 8) {
          int oldDword2 = memRead(ptrSockName+4);
          int lowermask = (1 << (bufferSize - 4) * 8) - 1;
          int highermask = -1 - lowermask;
          dword2 = (oldDword2 & highermask) | (dword2 & lowermask);
       }
       memWrite(ptrSockName + 4, dword2);
       return 0;
    }

    private int sys_getsockopt(int fdn, int level, int name, int valaddr, int lenaddr) throws ErrnoException, FaultException {
        SocketFD fd = getSocketFD(fdn);
        switch(level) {
            case SOL_SOCKET:
                switch(name) {
                    case SO_REUSEADDR:
                    case SO_KEEPALIVE: {
                        int len = memRead(lenaddr);
                        if(len < 4) return -EINVAL;
                        int val = (fd.options & name) != 0 ? 1 : 0;
                        memWrite(valaddr,val);
                        memWrite(lenaddr,4);
                        return 0;
                    }
                    default:
                        if(STDERR_DIAG) System.err.println("Unknown setsockopt name passed: " + name);
                        return -ENOPROTOOPT;
                }
            default:
                if(STDERR_DIAG) System.err.println("Unknown setsockopt leve passed: " + level);
                return -ENOPROTOOPT;
        } 
    }

    private int sys_bind(int fdn, int addr, int namelen) throws FaultException, ErrnoException {
        SocketFD fd = getSocketFD(fdn);

	if(fd.type() == SocketFD.TYPE_STREAM && (fd.s != null || fd.ss != null)) return -EISCONN;
        int word1 = memRead(addr);
        if( ((word1 >>> 16)&0xff) != AF_INET) return -EAFNOSUPPORT;
        int port = word1 & 0xffff;
        InetAddress inetAddr = null;
        if(memRead(addr+4) != 0) {
            byte[] ip = new byte[4];
            copyin(addr+4,ip,4);

            try {
                inetAddr = Platform.inetAddressFromBytes(ip);
            } catch(UnknownHostException e) {
                return -EADDRNOTAVAIL;
            }
        }

        switch(fd.type()) {
        case SocketFD.TYPE_STREAM: {
            fd.bindAddr = inetAddr;
            fd.bindPort = port;
            return 0;
        }
        case SocketFD.TYPE_DGRAM: {
            if(fd.ds != null) fd.ds.close();
            try {
                fd.ds = inetAddr != null ? new DatagramSocket(port,inetAddr) : new DatagramSocket(port);
            } catch(IOException e) {
                return -EADDRINUSE;
            }
            return 0;
        }
        default:
            throw new Error("should never happen");
        }
    }

    private int sys_listen(int fdn, int backlog) throws ErrnoException {
        SocketFD fd = getSocketFD(fdn);
        if(fd.type() != SocketFD.TYPE_STREAM) return -EOPNOTSUPP;
        if(fd.ss != null || fd.s != null) return -EISCONN;
        if(fd.bindPort < 0) return -EOPNOTSUPP;

        try {
            fd.ss = new ServerSocket(fd.bindPort,backlog,fd.bindAddr);
            fd.flags |= SocketFD.LISTEN;
            return 0;
        } catch(IOException e) {
            return -EADDRINUSE;
        }

    }

    private int sys_accept(int fdn, int addr, int lenaddr) throws ErrnoException, FaultException {
        SocketFD fd = getSocketFD(fdn);
        if(fd.type() != SocketFD.TYPE_STREAM) return -EOPNOTSUPP;
        if(!fd.listen()) return -EOPNOTSUPP;

        int size = memRead(lenaddr);

        ServerSocket s = fd.ss;
        Socket client;
        try {
            client = s.accept();
        } catch(IOException e) {
            return -EIO;
        }

        if(size >= 8) {
            memWrite(addr,(6 << 24) | (AF_INET << 16) | client.getPort());
            byte[] b = client.getInetAddress().getAddress();
            copyout(b,addr+4,4);
            memWrite(lenaddr,8);
        }

        SocketFD clientFD = new SocketFD(SocketFD.TYPE_STREAM);
        clientFD.s = client;
        try {
            clientFD.is = client.getInputStream();
            clientFD.os = client.getOutputStream();
        } catch(IOException e) {
            return -EIO;
        }
        int n = addFD(clientFD);
        if(n == -1) {
            clientFD.close();
            return -ENFILE;
        }
        return n;
    }

    private int sys_shutdown(int fdn, int how) throws ErrnoException {
        SocketFD fd = getSocketFD(fdn);
        if(fd.type() != SocketFD.TYPE_STREAM || fd.listen()) return -EOPNOTSUPP;
        if(fd.s == null) return -ENOTCONN;

        Socket s = fd.s;

        try {
            if(how == SHUT_RD || how == SHUT_RDWR) Platform.socketHalfClose(s,false);
            if(how == SHUT_WR || how == SHUT_RDWR) Platform.socketHalfClose(s,true);
        } catch(IOException e) {
            return -EIO;
        }

        return 0;
    }

    private int sys_sendto(int fdn, int addr, int count, int flags, int destAddr, int socklen) throws ErrnoException,ReadFaultException {
        SocketFD fd = getSocketFD(fdn);
        if(flags != 0) throw new ErrnoException(EINVAL);

        int word1 = memRead(destAddr);
        if( ((word1 >>> 16)&0xff) != AF_INET) return -EAFNOSUPPORT;
        int port = word1 & 0xffff;
        InetAddress inetAddr;
        byte[] ip = new byte[4];
        copyin(destAddr+4,ip,4);
        try {
            inetAddr = Platform.inetAddressFromBytes(ip);
        } catch(UnknownHostException e) {
            return -EADDRNOTAVAIL;
        }

        count = Math.min(count,MAX_CHUNK);
        byte[] buf = byteBuf(count);
        copyin(addr,buf,count);
        try {
            return fd.sendto(buf,0,count,inetAddr,port);
        } catch(ErrnoException e) {
            if(e.errno == EPIPE) exit(128+13,true);
            throw e;
        }
    }

    private int sys_recvfrom(int fdn, int addr, int count, int flags, int sourceAddr, int socklenAddr) throws ErrnoException, FaultException {
        SocketFD fd = getSocketFD(fdn);
        if(flags != 0) throw new ErrnoException(EINVAL);

        InetAddress[] inetAddr = sourceAddr == 0 ? null : new InetAddress[1];
        int[] port = sourceAddr == 0 ? null : new int[1];

        count = Math.min(count,MAX_CHUNK);
        byte[] buf = byteBuf(count);
        int n = fd.recvfrom(buf,0,count,inetAddr,port);
        copyout(buf,addr,n);

        if(sourceAddr != 0) {
            memWrite(sourceAddr,(AF_INET << 16) | port[0]);
            byte[] ip = inetAddr[0].getAddress();
            copyout(ip,sourceAddr+4,4);
        }

        return n;
    }

    private int sys_select(int n, int readFDs, int writeFDs, int exceptFDs, int timevalAddr) throws ReadFaultException, ErrnoException {
        return -ENOSYS;
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch(UnknownHostException e) {
            return "darkstar";
        }
    }

    private int sys_sysctl(int nameaddr, int namelen, int oldp, int oldlenaddr, int newp, int newlen) throws FaultException {
        if(newp != 0) return -EPERM;
        if(namelen == 0) return -ENOENT;
        if(oldp == 0) return 0;

        Object o = null;
        switch(memRead(nameaddr)) {
        case CTL_KERN:
            if(namelen != 2) break;
            switch(memRead(nameaddr+4)) {
            case KERN_OSTYPE:
                o = "NestedVM";
                break;
            case KERN_HOSTNAME:
                o = hostName();
                break;
            case KERN_OSRELEASE:
                o = VERSION;
                break;
            case KERN_VERSION:
                o = "NestedVM Kernel Version " + VERSION;
                break;
            }
            break;
        case CTL_HW:
            if(namelen != 2) break;
            switch(memRead(nameaddr+4)) {
            case HW_MACHINE:
                o = "NestedVM Virtual Machine";
                break;
            }
            break;
        }
        if(o == null) return -ENOENT;
        int len = memRead(oldlenaddr);
        if(o instanceof String) {
            byte[] b = getNullTerminatedBytes((String)o);
            if(len < b.length) return -ENOMEM;
            len = b.length;
            copyout(b,oldp,len);
            memWrite(oldlenaddr,len);
        } else if(o instanceof Integer) {
            if(len < 4) return -ENOMEM;
            memWrite(oldp,((Integer)o).intValue());
        } else {
            throw new Error("should never happen");
        }
        return 0;
    }
    
    public static final class GlobalState {
        Hashtable<String,GlobalState.CacheEnt> execCache = new Hashtable<String,GlobalState.CacheEnt>();

	final UnixRuntime[] tasks;
        int nextPID = 1;

	/** Table of all current file locks held by this process. */
        Seekable.Lock[] locks = new Seekable.Lock[16];

	private MP[] mps = new MP[0];
        private FS root;

	public GlobalState() { 
	    this(255); 
	}

        public GlobalState(int maxProcs) {
	   this(maxProcs,true); 
	}

        public GlobalState(int maxProcs, boolean defaultMounts) {
            tasks = new UnixRuntime[maxProcs+1];
            if(defaultMounts) {
                File root = null;
                if(Platform.getProperty("nestedvm.root") != null) {
                    root = new File(Platform.getProperty("nestedvm.root"));
                    if(!root.isDirectory()) throw new IllegalArgumentException("nestedvm.root is not a directory");
                } else {
                    String cwd = Platform.getProperty("user.dir");
                    root = Platform.getRoot(new File(cwd != null ? cwd : "."));
                }

                addMount("/",new HostFS(root));

                if(Platform.getProperty("nestedvm.root") == null) {
                    File[] roots = Platform.listRoots();
                    for(int i=0; i<roots.length; i++) {
                        String name = roots[i].getPath();
                        if(name.endsWith(File.separator))
                            name = name.substring(0,name.length()-1);
                        if(name.length() == 0 || name.indexOf('/') != -1) continue;
                        addMount("/" + name.toLowerCase(),new HostFS(roots[i]));
                    }
                }

                addMount("/dev",new DevFS());
                addMount("/resource",new ResourceFS());
                addMount("/cygdrive",new CygdriveFS());
            }
        }

        public String mapHostPath(String s) {
            return mapHostPath(new File(s));
        }

        public String mapHostPath(File f) {
            MP[] list;
            FS root;
            synchronized(this) {
                mps = this.mps;
                root = this.root;
            }
            if(!f.isAbsolute()) f = new File(f.getAbsolutePath());
            for(int i=mps.length; i>=0; i--) {
                FS fs = i == mps.length ? root : mps[i].fs;
                String path = i == mps.length ? "" : mps[i].path;
                if(!(fs instanceof HostFS)) continue;
                File fsroot = ((HostFS)fs).getRoot();
                if(!fsroot.isAbsolute()) fsroot = new File(fsroot.getAbsolutePath());
                if(f.getPath().startsWith(fsroot.getPath())) {
                    char sep = File.separatorChar;
                    String child = f.getPath().substring(fsroot.getPath().length());
                    if(sep != '/') {
                        char[] child_ = child.toCharArray();
                        for(int j=0; j<child_.length; j++) {
                            if(child_[j] == '/') child_[j] = sep;
                            else if(child_[j] == sep) child_[j] = '/';
                        }
                        child = new String(child_);
                    }
                    String mapped = "/" + (path.length()==0?"":path+"/") + child;
                    return mapped;
                }
            }
            return null;
        }

        static class MP implements Sort.Comparable {
            public MP(String path, FS fs) {
                this.path = path;
                this.fs = fs;
            }
            public String path;
            public FS fs;
            public int compareTo(Object o) {
                if(!(o instanceof MP)) return 1;
                return -path.compareTo(((MP)o).path);
            }
        }

        public synchronized FS getMount(String path) {
            if(!path.startsWith("/")) throw new IllegalArgumentException("Mount point doesn't start with a /");
            if(path.equals("/")) return root;
            path  = path.substring(1);
            for(int i=0; i<mps.length; i++)
                if(mps[i].path.equals(path)) return mps[i].fs;
            return null;
        }

        public synchronized void addMount(String path, FS fs) {
            if(getMount(path) != null) throw new IllegalArgumentException("mount point already exists");
            if(!path.startsWith("/")) throw new IllegalArgumentException("Mount point doesn't start with a /");

            if(fs.owner != null) fs.owner.removeMount(fs);
            fs.owner = this;

            if(path.equals("/")) {
                root = fs;
                fs.devno = 1;
                return;
            }
            path = path.substring(1);
            int oldLength = mps.length;
            MP[] newMPS = new MP[oldLength + 1];
            if(oldLength != 0) System.arraycopy(mps,0,newMPS,0,oldLength);
            newMPS[oldLength] = new MP(path,fs);
            Sort.sort(newMPS);
            mps = newMPS;
            int highdevno = 0;
            for(int i=0; i<mps.length; i++) highdevno = max(highdevno,mps[i].fs.devno);
            fs.devno = highdevno + 2;
        }

        public synchronized void removeMount(FS fs) {
            for(int i=0; i<mps.length; i++) if(mps[i].fs == fs) {
                    removeMount(i);
                    return;
                }
            throw new IllegalArgumentException("mount point doesn't exist");
        }

        public synchronized void removeMount(String path) {
            if(!path.startsWith("/")) throw new IllegalArgumentException("Mount point doesn't start with a /");
            if(path.equals("/")) {
                removeMount(-1);
            } else {
                path = path.substring(1);
                int p;
                for(p=0; p<mps.length; p++) if(mps[p].path.equals(path)) break;
                if(p == mps.length) throw new IllegalArgumentException("mount point doesn't exist");
                removeMount(p);
            }
        }

        private void removeMount(int index) {
            if(index == -1) {
                root.owner = null;
                root = null;
                return;
            }
            MP[] newMPS = new MP[mps.length - 1];
            System.arraycopy(mps,0,newMPS,0,index);
            System.arraycopy(mps,0,newMPS,index,mps.length-index-1);
            mps = newMPS;
        }

        private static class FSPathPair {
            private final FS filesystem;
            private final String path;
            public FSPathPair(FS filesystem, String path) {
                this.filesystem = filesystem;
                this.path = path;
            }

            public String getPath() {
                return path;
            }

            public FS getFS() {
                return filesystem;
            }
        }

        private FSPathPair findFS(String normalizedPath) {
            int pathLength = normalizedPath.length();
            if (pathLength != 0) {
                MP[] list;
                synchronized(this) {
                    list = mps;
                }
                for(int i = 0; i < list.length; i++) {
                    MP mp = list[i];
                    int mpl = mp.path.length();
                    if (normalizedPath.startsWith(mp.path) && (pathLength == mpl || normalizedPath.charAt(mpl) == '/')) {
                        return new FSPathPair(mp.fs, pathLength == mpl ? "" : normalizedPath.substring(mpl+1));
                    }
                }
            }
            return new FSPathPair(root, normalizedPath);

        }

        private Object fsop(int op, UnixRuntime r, String normalizedPath1, String normalizedPath2, int arg1, int arg2) throws ErrnoException {
            // We need to dispatch to the right filesystem.
            switch(op) {
            case FS.LINK:
                // Both paths must be in the same filesystem
                FSPathPair lookup1 = findFS(normalizedPath1), lookup2 = findFS(normalizedPath2);
                FS fs1 = lookup1.getFS(), fs2 = lookup2.getFS();
                if (fs1 != fs2) {
                    throw new ErrnoException(EXDEV);
                }
                return fs1.dispatch(op,r,lookup1.getPath(), lookup2.getPath(), arg1, arg2);
            default:
                throw new IllegalArgumentException("Unsupported operation: " + Integer.toString(op));
            }
        }

        private Object fsop(int op, UnixRuntime r, String normalizedPath, int arg1, int arg2) throws ErrnoException {
            int pl = normalizedPath.length();
            if(pl != 0) {
                MP[] list;
                synchronized(this) {
                    list = mps;
                }
                for(int i=0; i<list.length; i++) {
                    MP mp = list[i];
                    int mpl = mp.path.length();
                    if(normalizedPath.startsWith(mp.path) && (pl == mpl || normalizedPath.charAt(mpl) == '/'))
                        return mp.fs.dispatch(op,r,pl == mpl ? "" : normalizedPath.substring(mpl+1),arg1,arg2);
                }
            }
            return root.dispatch(op,r,normalizedPath,arg1,arg2);
        }

        public final FD open(UnixRuntime r, String path, int flags, int mode) throws ErrnoException {
            return (FD) fsop(FS.OPEN,r,path,flags,mode);
        }
        public final FStat stat(UnixRuntime r, String path) throws ErrnoException {
            return (FStat) fsop(FS.STAT,r,path,0,0);
        }
        public final FStat lstat(UnixRuntime r, String path) throws ErrnoException {
            return (FStat) fsop(FS.LSTAT,r,path,0,0);
        }
        public final void mkdir(UnixRuntime r, String path, int mode) throws ErrnoException {
            fsop(FS.MKDIR,r,path,mode,0);
        }
        public final void rmdir(UnixRuntime r, String path) throws ErrnoException {
            fsop(FS.RMDIR,r,path,0,0);
        }
        public final void unlink(UnixRuntime r, String path) throws ErrnoException {
            fsop(FS.UNLINK,r,path,0,0);
        }
        public final void link(UnixRuntime r, String oldpath, String newpath) throws ErrnoException {
            fsop(FS.LINK,r,oldpath,newpath, 0, 0);
        }

        private static class CacheEnt {
            public final long time;
            public final long size;
            public final Object o;
            public CacheEnt(long time, long size, Object o) {
                this.time = time;
                this.size = size;
                this.o = o;
            }
        }
    }

    public abstract static class FS {
        static final int OPEN = 1;
        static final int STAT = 2;
        static final int LSTAT = 3;
        static final int MKDIR = 4;
        static final int UNLINK = 5;
        static final int RMDIR = 6;
        static final int LINK = 7;

        GlobalState owner;
        int devno;

        Object dispatch(int op, UnixRuntime r, String path, int arg1, int arg2) throws ErrnoException {
            switch(op) {
            case OPEN:
                return open(r,path,arg1,arg2);
            case STAT:
                return stat(r,path);
            case LSTAT:
                return lstat(r,path);
            case MKDIR:
                mkdir(r,path,arg1);
                return null;
            case UNLINK:
                unlink(r,path);
                return null;
            case RMDIR:
                rmdir(r,path);
                return null;
            default:
                throw new IllegalArgumentException("Invalid FSOP: " + Integer.toString(op));
            }
        }

        Object dispatch(int op, UnixRuntime r, String path1, String path2, int arg1, int arg2) throws ErrnoException {
            switch(op) {
            case LINK:
                link(r, path1, path2);
                return null;
            default:
                throw new IllegalArgumentException("Invalid FSOP: " + Integer.toString(op));
            }
        }

        public FStat lstat(UnixRuntime r, String path) throws ErrnoException {
            return stat(r,path);
        }

        // If this returns null it'll be truned into an ENOENT
        public abstract FD open(UnixRuntime r, String path, int flags, int mode) throws ErrnoException;
        // If this returns null it'll be turned into an ENOENT
        public abstract FStat stat(UnixRuntime r, String path) throws ErrnoException;
        public abstract void mkdir(UnixRuntime r, String path, int mode) throws ErrnoException;
        public abstract void unlink(UnixRuntime r, String path) throws ErrnoException;
        public abstract void rmdir(UnixRuntime r, String path) throws ErrnoException;
        public abstract void link(UnixRuntime r, String oldpath, String newpath) throws ErrnoException;
    }

    // chroot support should go in here if it is ever implemented
    private String normalizePath(String path) {
        boolean absolute = path.startsWith("/");
        int cwdl = cwd.length();

        // NOTE: This isn't just a fast path, it handles cases the code below doesn't
        if(!path.startsWith(".") && path.indexOf("./") == -1 && path.indexOf("//") == -1 && !path.endsWith("."))
            return absolute ? path.substring(1) : cwdl == 0 ? path : path.length() == 0 ? cwd : cwd + "/" + path;

        char[] in = new char[path.length()+1];
        char[] out = new char[in.length + (absolute ? -1 : cwd.length())];
        path.getChars(0,path.length(),in,0);
        int inp=0, outp=0;

        if(absolute) {
            do {
                inp++;
            }
            while(in[inp] == '/');
        } else if(cwdl != 0) {
            cwd.getChars(0,cwdl,out,0);
            outp = cwdl;
        }

        while(in[inp] != 0) {
            if(inp != 0) {
                while(in[inp] != 0 && in[inp] != '/') {
                    out[outp++] = in[inp++];
                }
                if(in[inp] == '\0') break;
                while(in[inp] == '/') inp++;
            }

            // Just read a /
            if(in[inp] == '\0') break;
            if(in[inp] != '.') {
                out[outp++] = '/';
                out[outp++] = in[inp++];
                continue;
            }
            // Just read a /.
            if(in[inp+1] == '\0' || in[inp+1] == '/') {
                inp++;
                continue;
            }
            if(in[inp+1] == '.' && (in[inp+2] == '\0' || in[inp+2] == '/')) { // ..
                // Just read a /..{$,/}
                inp += 2;
                if(outp > 0) outp--;
                while(outp > 0 && out[outp] != '/') outp--;
                //System.err.println("After ..: " + new String(out,0,outp));
                continue;
            }
            // Just read a /.[^.] or /..[^/$]
            inp++;
            out[outp++] = '/';
            out[outp++] = '.';
        }
        if(outp > 0 && out[outp-1] == '/') outp--;
        //System.err.println("normalize: " + path + " -> " + new String(out,0,outp) + " (cwd: " + cwd + ")");
        int outStart = out[0] == '/' ? 1 : 0;
        return new String(out,outStart,outp - outStart);
    }

    FStat hostFStat(final File f, Object data) {
        boolean e = false;
        try {
            FileInputStream fis = new FileInputStream(f);
            switch(fis.read()) {
            case '\177':
                e = fis.read() == 'E' && fis.read() == 'L' && fis.read() == 'F';
                break;
            case '#':
                e = fis.read() == '!';
            }
            fis.close();
        } catch(IOException e2) { }
        HostFS fs = (HostFS) data;
        final int inode = fs.inodes.get(f.getAbsolutePath());
        final int devno = fs.devno;
        return new HostFStat(f,e) {
            public int inode() {
                return inode;
            }
            public int dev() {
                return devno;
            }
        };
    }

    FD hostFSDirFD(File f, Object _fs) {
        HostFS fs = (HostFS) _fs;
        return fs.new HostDirFD(f);
    }

    public static class HostFS extends FS {
        InodeCache inodes = new InodeCache(4000);
        protected File root;
        public File getRoot() {
            return root;
        }

        protected File hostFile(String path) {
            char sep = File.separatorChar;
            if(sep != '/') {
                char buf[] = path.toCharArray();
                for(int i=0; i<buf.length; i++) {
                    char c = buf[i];
                    if(c == '/') buf[i] = sep;
                    else if(c == sep) buf[i] = '/';
                }
                path = new String(buf);
            }
            return new File(root,path);
        }

        public HostFS(String root) {
            this(new File(root));
        }
        public HostFS(File root) {
            this.root = root;
        }

        public FD open(UnixRuntime r, String path, int flags, int mode) throws ErrnoException {
            final File f = hostFile(path);
            return r.hostFSOpen(f,flags,mode,this);
        }

        public void rmdir(UnixRuntime r, String path) throws ErrnoException {
            unlink(r, path);
        }

        public void unlink(UnixRuntime r, String path) throws ErrnoException {
            File f = hostFile(path);
            if(r.sm != null && !r.sm.allowUnlink(f)) throw new ErrnoException(EPERM);
            if(!f.exists()) throw new ErrnoException(ENOENT);
            if(!f.delete()) {
                // Can't delete file immediately, so mark for
                // delete on close all matching FDs
                boolean marked = false;
                for(int i=0; i<OPEN_MAX; i++) {
                    if(r.fds[i] != null) {
                        String fdpath = r.fds[i].getNormalizedPath();
                        if(fdpath != null && fdpath.equals(path)) {
                            r.fds[i].markDeleteOnClose();
                            marked = true;
                        }
                    }
                }
                if(!marked) throw new ErrnoException(EPERM);
            }
        }

        public void link(UnixRuntime r, String oldpath, String newpath) throws ErrnoException {
            try {
                Files.createLink(hostFile(newpath).toPath(), hostFile(oldpath).toPath());
            } catch (IOException ex) {
                throw new ErrnoException(EIO);
            }
        }

        public FStat stat(UnixRuntime r, String path) throws ErrnoException {
            File f = hostFile(path);
            if(r.sm != null && !r.sm.allowStat(f)) throw new ErrnoException(EACCES);
            if(!f.exists()) return null;
            return r.hostFStat(f,this);
        }

        public void mkdir(UnixRuntime r, String path, int mode) throws ErrnoException {
            File f = hostFile(path);
            if(r.sm != null && !r.sm.allowWrite(f)) throw new ErrnoException(EACCES);
            if(f.exists() && f.isDirectory()) throw new ErrnoException(EEXIST);
            if(f.exists()) throw new ErrnoException(ENOTDIR);
            File parent = getParentFile(f);
            if(parent!=null && (!parent.exists() || !parent.isDirectory())) throw new ErrnoException(ENOTDIR);
            if(!f.mkdir()) throw new ErrnoException(EIO);
        }

        private static File getParentFile(File f) {
            String p = f.getParent();
            return p == null ? null : new File(p);
        }

        public class HostDirFD extends DirFD {
            private final File f;
            private final File[] children;
            public HostDirFD(File f) {
                this.f = f;
                String[] l = f.list();
                children = new File[l.length];
                for(int i=0; i<l.length; i++) children[i] = new File(f,l[i]);
            }
            public int size() {
                return children.length;
            }
            public String name(int n) {
                return children[n].getName();
            }
            public int inode(int n) {
                return inodes.get(children[n].getAbsolutePath());
            }
            public int parentInode() {
                File parent = getParentFile(f);
                // HACK: myInode() isn't really correct  if we're not the root
                return parent == null ? myInode() : inodes.get(parent.getAbsolutePath());
            }
            public int myInode() {
                return inodes.get(f.getAbsolutePath());
            }
            public int myDev() {
                return devno;
            }
        }
    }

    /* Implements the Cygwin notation for accessing MS Windows drive letters
     * in a unix path. The path /cygdrive/c/myfile is converted to C:\file.
     * As there is no POSIX standard for this, little checking is done. */
    public static class CygdriveFS extends HostFS {
        protected File hostFile(String path) {
            java.util.logging.Logger.getLogger(CygdriveFS.class.getName()).fine("path = " + path);
            final char drive = path.charAt(0);

            if (drive < 'a' || drive > 'z' || (path.length() > 1 && path.charAt(1) != '/'))
                return null;
            if (path.length() == 1) {
                path = path + "/";
            }

            path = drive + ":" + path.substring(1).replace('/', '\\');
            java.util.logging.Logger.getLogger(CygdriveFS.class.getName()).fine("path new = " + path);
            return new File(path);
        }

        public CygdriveFS() {
            super("/");
        }
    }

    private static void putInt(byte[] buf, int off, int n) {
        buf[off+0] = (byte)((n>>>24)&0xff);
        buf[off+1] = (byte)((n>>>16)&0xff);
        buf[off+2] = (byte)((n>>> 8)&0xff);
        buf[off+3] = (byte)((n>>> 0)&0xff);
    }

    public static abstract class DirFD extends FD {
        private int pos = -2;

        protected abstract int size();
        protected abstract String name(int n);
        protected abstract int inode(int n);
        protected abstract int myDev();
        protected abstract int parentInode();
        protected abstract int myInode();
        public int flags() {
            return O_RDONLY;
        }

        public int getdents(byte[] buf, int off, int len) {
            int ooff = off;
            int ino;
            int reclen;
            OUTER: for(; len > 0 && pos < size(); pos++) {
                switch(pos) {
                case -2:
                case -1:
                    ino = pos == -1 ? parentInode() : myInode();
                    if(ino == -1) continue;
                    reclen = 9 + (pos == -1 ? 2 : 1);
                    if(reclen > len) break OUTER;
                    buf[off+8] = '.';
                    if(pos == -1) buf[off+9] = '.';
                    break;
                default: {
                    String f = name(pos);
                    byte[] fb = getBytes(f);
                    reclen = fb.length + 9;
                    if(reclen > len) break OUTER;
                    ino = inode(pos);
                    System.arraycopy(fb,0,buf,off+8,fb.length);
                }
                }
                buf[off+reclen-1] = 0; // null terminate
                reclen = (reclen + 3) & ~3; // add padding
                putInt(buf,off,reclen);
                putInt(buf,off+4,ino);
                off += reclen;
                len -= reclen;
            }
            return off-ooff;
        }

        protected FStat _fstat() {
            return new FStat() {
                public int type() {
                    return S_IFDIR;
                }
                public int inode() {
                    return myInode();
                }
                public int dev() {
                    return myDev();
                }
            };
        }
    }

    public static class DevFS extends FS {
        private static final int ROOT_INODE = 1;
        private static final int NULL_INODE = 2;
        private static final int ZERO_INODE = 3;
        private static final int FD_INODE = 4;
        private static final int FD_INODES = 32;

        private abstract class DevFStat extends FStat {
            public int dev() {
                return devno;
            }
            public int mode() {
                return 0666;
            }
            public int type() {
                return S_IFCHR;
            }
            public int nlink() {
                return 1;
            }
            public abstract int inode();
        }

        private abstract class DevDirFD extends DirFD {
            public int myDev() {
                return devno;
            }
        }

        private FD devZeroFD = new FD() {
            public int read(byte[] a, int off, int length) {
                /*Arrays.fill(a,off,off+length,(byte)0);*/
                for(int i=off; i<off+length; i++) a[i] = 0;
                return length;
            }
            public int write(byte[] a, int off, int length) {
                return length;
            }
            public int seek(int n, int whence) {
                return 0;
            }
            public FStat _fstat() {
                return new DevFStat() {
                    public int inode() {
                        return ZERO_INODE;
                    }
                };
            }
            public int flags() {
                return O_RDWR;
            }
        };
        private FD devNullFD = new FD() {
            public int read(byte[] a, int off, int length) {
                return 0;
            }
            public int write(byte[] a, int off, int length) {
                return length;
            }
            public int seek(int n, int whence) {
                return 0;
            }
            public FStat _fstat() {
                return new DevFStat() {
                    public int inode() {
                        return NULL_INODE;
                    }
                };
            }
            public int flags() {
                return O_RDWR;
            }
        };

        public FD open(UnixRuntime r, String path, int mode, int flags) throws ErrnoException {
            if(path.equals("null")) return devNullFD;
            if(path.equals("zero")) return devZeroFD;
            if(path.startsWith("fd/")) {
                int n;
                try {
                    n = Integer.parseInt(path.substring(3));
                } catch(NumberFormatException e) {
                    return null;
                }
                if(n < 0 || n >= OPEN_MAX) return null;
                if(r.fds[n] == null) return null;
                return r.fds[n].dup();
            }
            if(path.equals("fd")) {
                int count=0;
                for(int i=0; i<OPEN_MAX; i++) if(r.fds[i] != null) {
                        count++;
                    }
                final int[] files = new int[count];
                count = 0;
                for(int i=0; i<OPEN_MAX; i++) if(r.fds[i] != null) files[count++] = i;
                return new DevDirFD() {
                    public int myInode() {
                        return FD_INODE;
                    }
                    public int parentInode() {
                        return ROOT_INODE;
                    }
                    public int inode(int n) {
                        return FD_INODES + n;
                    }
                    public String name(int n) {
                        return Integer.toString(files[n]);
                    }
                    public int size() {
                        return files.length;
                    }
                };
            }
            if(path.equals("")) {
                return new DevDirFD() {
                    public int myInode() {
                        return ROOT_INODE;
                    }
                    // HACK: We don't have any clean way to get the parent inode
                    public int parentInode() {
                        return ROOT_INODE;
                    }
                    public int inode(int n) {
                        switch(n) {
                        case 0:
                            return NULL_INODE;
                        case 1:
                            return ZERO_INODE;
                        case 2:
                            return FD_INODE;
                        default:
                            return -1;
                        }
                    }

                    public String name(int n) {
                        switch(n) {
                        case 0:
                            return "null";
                        case 1:
                            return "zero";
                        case 2:
                            return "fd";
                        default:
                            return null;
                        }
                    }
                    public int size() {
                        return 3;
                    }
                };
            }
            return null;
        }

        public FStat stat(UnixRuntime r,String path) throws ErrnoException {
            if(path.equals("null")) return devNullFD.fstat();
            if(path.equals("zero")) return devZeroFD.fstat();
            if(path.startsWith("fd/")) {
                int n;
                try {
                    n = Integer.parseInt(path.substring(3));
                } catch(NumberFormatException e) {
                    return null;
                }
                if(n < 0 || n >= OPEN_MAX) return null;
                if(r.fds[n] == null) return null;
                return r.fds[n].fstat();
            }
            if(path.equals("fd")) return new FStat() {
                public int inode() {
                    return FD_INODE;
                }
                public int dev() {
                    return devno;
                }
                public int type() {
                    return S_IFDIR;
                }
                public int mode() {
                    return 0444;
                }
            };
            if(path.equals(""))   return new FStat() {
                public int inode() {
                    return ROOT_INODE;
                }
                public int dev() {
                    return devno;
                }
                public int type() {
                    return S_IFDIR;
                }
                public int mode() {
                    return 0444;
                }
            };
            return null;
        }

        public void mkdir(UnixRuntime r, String path, int mode) throws ErrnoException {
            throw new ErrnoException(EROFS);
        }
        public void rmdir(UnixRuntime r, String path) throws ErrnoException {
            throw new ErrnoException(EROFS);
        }
        public void unlink(UnixRuntime r, String path) throws ErrnoException {
            throw new ErrnoException(EROFS);
        }
        public void link(UnixRuntime r, String oldpath, String newpath) throws ErrnoException {
            throw new ErrnoException(EROFS);
        }
    }


    public static class ResourceFS extends FS {
        final InodeCache inodes = new InodeCache(500);

        public FStat lstat(UnixRuntime r, String path) throws ErrnoException {
            return stat(r,path);
        }
        public void mkdir(UnixRuntime r, String path, int mode) throws ErrnoException {
            throw new ErrnoException(EROFS);
        }
        public void unlink(UnixRuntime r, String path) throws ErrnoException {
            throw new ErrnoException(EROFS);
        }
        public void rmdir(UnixRuntime r, String path) throws ErrnoException {
            throw new ErrnoException(EROFS);
        }
        public void link(UnixRuntime r, String oldpath, String newpath) throws ErrnoException {
            throw new ErrnoException(EROFS);
        }

        FStat connFStat(final URLConnection conn) {
            return new FStat() {
                public int type() {
                    return S_IFREG;
                }
                public int nlink() {
                    return 1;
                }
                public int mode() {
                    return 0444;
                }
                public int size() {
                    return conn.getContentLength();
                }
                public int mtime() {
                    return (int)(conn.getDate() / 1000);
                }
                public int inode() {
                    return inodes.get(conn.getURL().toString());
                }
                public int dev() {
                    return devno;
                }
            };
        }

        public FStat stat(UnixRuntime r, String path) throws ErrnoException {
            URL url = r.getClass().getResource("/" + path);
            if(url == null) return null;
            try {
                return connFStat(url.openConnection());
            } catch(IOException e) {
                throw new ErrnoException(EIO);
            }
        }

        public FD open(UnixRuntime r, String path, int flags, int mode) throws ErrnoException {
            if((flags & ~3) != 0) {
                if(STDERR_DIAG)
                    System.err.println("WARNING: Unsupported flags passed to ResourceFS.open(\"" + path + "\"): " + toHex(flags & ~3));
                throw new ErrnoException(ENOTSUP);
            }
            if((flags&3) != RD_ONLY) throw new ErrnoException(EROFS);
            URL url = r.getClass().getResource("/" + path);
            if(url == null) return null;
            try {
                final URLConnection conn = url.openConnection();
                Seekable.InputStream si = new Seekable.InputStream(conn.getInputStream());
                return new SeekableFD(si,flags) {
                    protected FStat _fstat() {
                        return connFStat(conn);
                    }
                };
            } catch(FileNotFoundException e) {
                if(e.getMessage() != null && e.getMessage().indexOf("Permission denied") >= 0) throw new ErrnoException(EACCES);
                return null;
            } catch(IOException e) {
                throw new ErrnoException(EIO);
            }
        }
    }
}
