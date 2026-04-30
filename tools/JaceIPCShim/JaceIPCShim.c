/*
 * JaceIPCShim — Windows bridge between Cyrene.exe and Jace's TCP IPC server.
 *
 * Build: gcc -o JaceIPCShim.exe JaceIPCShim.c -lws2_32 -luser32 -mconsole
 * Usage: JaceIPCShim.exe [port]   (default port: 57867)
 *
 * This program pretends to be KEGS toward Cyrene.exe, forwarding all
 * WM_COPYDATA messages to Jace over a local TCP socket.
 *
 * Protocol overview:
 *   Cyrene -> shim : WM_COPYDATA (Win32 message)
 *   Shim   -> Jace : TCP frame [type:4][len:4][payload:N] (little-endian)
 *   Jace   -> shim : TCP frame [type:4][len:4][payload:N] (little-endian)
 *   Shim   -> Cyrene : WM_COPYDATA (Win32 message)
 *
 * IPC constants match jace.ipc.IpcConstants exactly.
 */

#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <winsock2.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* ---------------------------------------------------------------------------
 * IPC constants — must match jace.ipc.IpcConstants exactly
 * ---------------------------------------------------------------------------*/
#define C2K_OPEN_CONNECTION   1   /* Cyrene -> KEGS: open session            */
#define C2K_CLOSE_CONNECTION  2   /* Cyrene -> KEGS: close session           */
#define C2K_GET_SNAPSHOT      3   /* Cyrene -> KEGS: request full snapshot   */
#define C2K_GET_OPERATION     4   /* Cyrene -> KEGS: request next operation  */
#define C2K_PAUSE             5   /* Cyrene -> KEGS: pause emulation         */
#define C2K_WRITE_DATA        6   /* Cyrene -> KEGS: write memory            */
#define K2C_SEND_SNAPSHOT     6   /* KEGS -> Cyrene: full snapshot response  */
#define K2C_SEND_OPERATION    7   /* KEGS -> Cyrene: operation data          */
#define K2C_CLOSE_CONNECTION  9   /* KEGS -> Cyrene: closing connection      */

#define FRAME_HEADER_SIZE     8   /* 4-byte type + 4-byte length             */
#define DEFAULT_PORT          57867

/* Shared memory layout constants (from Window_IPC.h) */
#define IPC_SM_HEADER_SIZE       (1 * 1024)
#define IPC_SM_BREAKPOINT_SIZE   (4 * 1024)
#define IPC_SM_OUTPUT_SIZE       (1024 * 1024)
#define IPC_SM_TOTAL_SIZE        (IPC_SM_HEADER_SIZE + IPC_SM_BREAKPOINT_SIZE + IPC_SM_OUTPUT_SIZE)

/* Window class names */
#define KEGS_IPC_WINDOW_CLASS    "KegsIPC"
#define CYRENE_IPC_WINDOW_CLASS  "CyreneIPC"

/* Cyrene search timeout: 10 seconds, polled every 500 ms */
#define CYRENE_FIND_TIMEOUT_MS   10000
#define CYRENE_FIND_POLL_MS      500

/* Maximum receive buffer for a single TCP frame payload (snapshot ~ 400 KB) */
#define MAX_PAYLOAD_SIZE         (512 * 1024)

/* ---------------------------------------------------------------------------
 * Globals
 * ---------------------------------------------------------------------------*/
static SOCKET         g_tcp_sock     = INVALID_SOCKET;
static HWND           g_hwnd_shim    = NULL;
static volatile HWND  g_hwnd_cyrene  = NULL;
static HANDLE         g_hSharedMem   = NULL;
static unsigned char *g_pSharedMem   = NULL;
static CRITICAL_SECTION g_cs_send;   /* protects TCP sends from WndProc */

/* ---------------------------------------------------------------------------
 * Forward declarations
 * ---------------------------------------------------------------------------*/
static LRESULT CALLBACK WndProc(HWND, UINT, WPARAM, LPARAM);
static DWORD WINAPI ReceiveThread(LPVOID);
static int  tcp_send_frame(int type, const unsigned char *data, int data_len);
static int  tcp_recv_frame(int *out_type, unsigned char **out_data, int *out_len);
static void write_le32(unsigned char *buf, int val);
static int  read_le32(const unsigned char *buf);
static HWND find_cyrene_window(void);
static int  create_shared_memory(void);

/* ---------------------------------------------------------------------------
 * Utility: write/read 32-bit little-endian integers
 * ---------------------------------------------------------------------------*/
static void write_le32(unsigned char *buf, int val)
{
    buf[0] = (unsigned char)(val & 0xFF);
    buf[1] = (unsigned char)((val >> 8) & 0xFF);
    buf[2] = (unsigned char)((val >> 16) & 0xFF);
    buf[3] = (unsigned char)((val >> 24) & 0xFF);
}

static int read_le32(const unsigned char *buf)
{
    return (int)((unsigned int)buf[0]
              | ((unsigned int)buf[1] << 8)
              | ((unsigned int)buf[2] << 16)
              | ((unsigned int)buf[3] << 24));
}

/* ---------------------------------------------------------------------------
 * TCP frame I/O
 *
 * send: [4-byte type LE][4-byte len LE][payload]
 * recv: same layout; caller must free *out_data
 * ---------------------------------------------------------------------------*/
static int tcp_send_frame(int type, const unsigned char *data, int data_len)
{
    unsigned char hdr[FRAME_HEADER_SIZE];
    int sent, total;
    const unsigned char *ptr;

    write_le32(hdr, type);
    write_le32(hdr + 4, data_len);

    EnterCriticalSection(&g_cs_send);

    /* Send header */
    total = 0;
    ptr   = hdr;
    while (total < FRAME_HEADER_SIZE) {
        sent = send(g_tcp_sock, (const char *)ptr + total,
                    FRAME_HEADER_SIZE - total, 0);
        if (sent == SOCKET_ERROR || sent == 0) {
            LeaveCriticalSection(&g_cs_send);
            return -1;
        }
        total += sent;
    }

    /* Send payload (may be zero length) */
    total = 0;
    ptr   = data;
    while (total < data_len) {
        sent = send(g_tcp_sock, (const char *)ptr + total,
                    data_len - total, 0);
        if (sent == SOCKET_ERROR || sent == 0) {
            LeaveCriticalSection(&g_cs_send);
            return -1;
        }
        total += sent;
    }

    LeaveCriticalSection(&g_cs_send);
    return 0;
}

static int tcp_recv_frame(int *out_type, unsigned char **out_data, int *out_len)
{
    unsigned char hdr[FRAME_HEADER_SIZE];
    int rcvd, total, payload_len;
    unsigned char *payload;

    /* Read header */
    total = 0;
    while (total < FRAME_HEADER_SIZE) {
        rcvd = recv(g_tcp_sock, (char *)hdr + total,
                    FRAME_HEADER_SIZE - total, 0);
        if (rcvd == SOCKET_ERROR || rcvd == 0)
            return -1;
        total += rcvd;
    }

    *out_type   = read_le32(hdr);
    payload_len = read_le32(hdr + 4);

    if (payload_len < 0 || payload_len > MAX_PAYLOAD_SIZE) {
        printf("[shim] Oversized frame payload: %d bytes — dropping\n", payload_len);
        return -1;
    }

    payload = NULL;
    if (payload_len > 0) {
        payload = (unsigned char *)malloc(payload_len);
        if (payload == NULL)
            return -1;

        total = 0;
        while (total < payload_len) {
            rcvd = recv(g_tcp_sock, (char *)payload + total,
                        payload_len - total, 0);
            if (rcvd == SOCKET_ERROR || rcvd == 0) {
                free(payload);
                return -1;
            }
            total += rcvd;
        }
    }

    *out_data = payload;
    *out_len  = payload_len;
    return 0;
}

/* ---------------------------------------------------------------------------
 * Find Cyrene's IPC window.
 * Cyrene registers class "CyreneIPC" with title "Cyrene_<PID>".
 * We use FindWindow with the class name only (NULL title) so we don't need
 * to know Cyrene's PID in advance.
 * ---------------------------------------------------------------------------*/
static HWND find_cyrene_window(void)
{
    return FindWindow(CYRENE_IPC_WINDOW_CLASS, NULL);
}

/* ---------------------------------------------------------------------------
 * Create the Windows Named Shared Memory that Cyrene maps into its process.
 * Cyrene opens: "Local\Cyrene_<KEGS_PID>" where KEGS_PID is the shim's PID.
 * The shim creates the mapping; Cyrene opens (maps) it read/write.
 * ---------------------------------------------------------------------------*/
static int create_shared_memory(void)
{
    char name[256];
    DWORD shim_pid = GetCurrentProcessId();

    sprintf(name, "Local\\Cyrene_%lu", (unsigned long)shim_pid);

    g_hSharedMem = CreateFileMapping(
        INVALID_HANDLE_VALUE,     /* backed by page file */
        NULL,                     /* default security    */
        PAGE_READWRITE,
        0,                        /* high size DWORD     */
        IPC_SM_TOTAL_SIZE,        /* low size DWORD      */
        name
    );

    if (g_hSharedMem == NULL) {
        printf("[shim] CreateFileMapping failed: %lu\n",
               (unsigned long)GetLastError());
        return -1;
    }

    g_pSharedMem = (unsigned char *)MapViewOfFile(
        g_hSharedMem, FILE_MAP_ALL_ACCESS, 0, 0, IPC_SM_TOTAL_SIZE
    );

    if (g_pSharedMem == NULL) {
        printf("[shim] MapViewOfFile failed: %lu\n",
               (unsigned long)GetLastError());
        CloseHandle(g_hSharedMem);
        g_hSharedMem = NULL;
        return -1;
    }

    memset(g_pSharedMem, 0, IPC_SM_TOTAL_SIZE);
    printf("[shim] Shared memory '%s' created (%d bytes)\n",
           name, IPC_SM_TOTAL_SIZE);
    return 0;
}

/* ---------------------------------------------------------------------------
 * Receive thread: reads TCP frames from Jace and forwards to Cyrene.
 * Runs until TCP disconnects, then posts WM_QUIT to the main thread.
 * ---------------------------------------------------------------------------*/
static DWORD WINAPI ReceiveThread(LPVOID arg)
{
    int  frame_type, payload_len, rc;
    unsigned char *payload;
    COPYDATASTRUCT cds;
    HWND cyrene_hwnd;
    (void)arg;

    printf("[shim] Receive thread started\n");

    for (;;) {
        payload     = NULL;
        payload_len = 0;

        rc = tcp_recv_frame(&frame_type, &payload, &payload_len);
        if (rc != 0) {
            printf("[shim] TCP receive error or connection closed — exiting\n");
            break;
        }

        printf("[shim] <- Jace: type=%d len=%d\n", frame_type, payload_len);

        cyrene_hwnd = g_hwnd_cyrene;
        if (cyrene_hwnd == NULL)
            cyrene_hwnd = find_cyrene_window();

        if (cyrene_hwnd != NULL) {
            g_hwnd_cyrene = cyrene_hwnd;

            memset(&cds, 0, sizeof(cds));
            cds.dwData = (ULONG_PTR)frame_type;
            cds.cbData = (DWORD)payload_len;
            cds.lpData = payload;

            /* SendMessage is synchronous: waits for Cyrene to process it */
            SendMessage(cyrene_hwnd, WM_COPYDATA,
                        (WPARAM)g_hwnd_shim, (LPARAM)&cds);
        } else {
            printf("[shim] Cyrene window not found — discarding frame type=%d\n",
                   frame_type);
        }

        if (frame_type == K2C_CLOSE_CONNECTION) {
            printf("[shim] Server closed connection (K2C_CLOSE_CONNECTION)\n");
            if (payload) free(payload);
            break;
        }

        if (payload)
            free(payload);
    }

    /* Signal the main message loop to exit */
    PostMessage(g_hwnd_shim, WM_QUIT, 0, 0);
    return 0;
}

/* ---------------------------------------------------------------------------
 * Window procedure for the shim's "KegsIPC" window.
 * Handles WM_COPYDATA from Cyrene: encodes as TCP frame and forwards to Jace.
 * ---------------------------------------------------------------------------*/
static LRESULT CALLBACK WndProc(HWND hwnd, UINT msg,
                                  WPARAM wParam, LPARAM lParam)
{
    COPYDATASTRUCT *cds;
    int ipc_type, rc;

    switch (msg) {

    case WM_COPYDATA:
        cds      = (COPYDATASTRUCT *)lParam;
        ipc_type = (int)cds->dwData;

        printf("[shim] -> Jace: WM_COPYDATA type=%d len=%lu\n",
               ipc_type, (unsigned long)cds->cbData);

        /* Forward to Jace as a TCP frame */
        rc = tcp_send_frame(ipc_type,
                            (const unsigned char *)cds->lpData,
                            (int)cds->cbData);
        if (rc != 0) {
            printf("[shim] TCP send failed for type=%d\n", ipc_type);
            PostQuitMessage(1);
            return 0;
        }

        /*
         * WM_COPYDATA from Cyrene is always a request that expects an
         * async response: Jace will send the response independently via
         * the receive thread using its own WM_COPYDATA back to Cyrene.
         * Return 1 to tell Cyrene the message was accepted.
         */
        return 1;

    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;
    }

    return DefWindowProc(hwnd, msg, wParam, lParam);
}

/* ---------------------------------------------------------------------------
 * main
 * ---------------------------------------------------------------------------*/
int main(int argc, char *argv[])
{
    int          port = DEFAULT_PORT;
    WSADATA      wsa_data;
    SOCKET       sock;
    struct sockaddr_in server_addr;
    HINSTANCE    hInstance;
    WNDCLASSEX   wc;
    char         window_title[64];
    HANDLE       recv_thread;
    DWORD        thread_id;
    HWND         cyrene_hwnd;
    MSG          msg;
    int          i, found;

    /* Parse optional port argument */
    if (argc >= 2)
        port = atoi(argv[1]);
    if (port <= 0 || port > 65535)
        port = DEFAULT_PORT;

    printf("[shim] JaceIPCShim starting — Jace port %d\n", port);

    /* Initialize Winsock */
    if (WSAStartup(MAKEWORD(2, 2), &wsa_data) != 0) {
        MessageBox(NULL, "WSAStartup failed", "JaceIPCShim", MB_OK | MB_ICONERROR);
        return 1;
    }

    /* Connect TCP socket to Jace */
    sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (sock == INVALID_SOCKET) {
        printf("[shim] socket() failed: %d\n", WSAGetLastError());
        MessageBox(NULL, "Failed to create socket", "JaceIPCShim",
                   MB_OK | MB_ICONERROR);
        WSACleanup();
        return 1;
    }

    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family      = AF_INET;
    server_addr.sin_addr.s_addr = inet_addr("127.0.0.1");
    server_addr.sin_port        = htons((unsigned short)port);

    if (connect(sock, (struct sockaddr *)&server_addr, sizeof(server_addr))
            == SOCKET_ERROR) {
        char errmsg[256];
        sprintf(errmsg, "Cannot connect to Jace on 127.0.0.1:%d\n"
                        "Is Jace running with Cyrene IPC enabled?", port);
        printf("[shim] %s\n", errmsg);
        MessageBox(NULL, errmsg, "JaceIPCShim — Connection Failed",
                   MB_OK | MB_ICONERROR);
        closesocket(sock);
        WSACleanup();
        return 1;
    }

    printf("[shim] Connected to Jace at 127.0.0.1:%d\n", port);
    g_tcp_sock = sock;

    /* Initialize critical section protecting TCP sends */
    InitializeCriticalSection(&g_cs_send);

    /* Create shared memory (Cyrene opens it by our PID) */
    if (create_shared_memory() != 0) {
        printf("[shim] Warning: shared memory creation failed — "
               "breakpoint output will not work\n");
        /* Non-fatal: continue without shared memory */
    }

    /* Register "KegsIPC" window class */
    hInstance = GetModuleHandle(NULL);
    memset(&wc, 0, sizeof(wc));
    wc.cbSize        = sizeof(WNDCLASSEX);
    wc.style         = 0;
    wc.lpfnWndProc   = WndProc;
    wc.cbClsExtra    = 0;
    wc.cbWndExtra    = 0;
    wc.hInstance     = hInstance;
    wc.hIcon         = LoadIcon(NULL, IDI_APPLICATION);
    wc.hCursor       = LoadCursor(NULL, IDC_ARROW);
    wc.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);
    wc.lpszMenuName  = NULL;
    wc.lpszClassName = KEGS_IPC_WINDOW_CLASS;
    wc.hIconSm       = LoadIcon(NULL, IDI_APPLICATION);

    if (!RegisterClassEx(&wc)) {
        printf("[shim] RegisterClassEx failed: %lu\n",
               (unsigned long)GetLastError());
        MessageBox(NULL, "RegisterClassEx failed", "JaceIPCShim",
                   MB_OK | MB_ICONERROR);
        closesocket(g_tcp_sock);
        WSACleanup();
        DeleteCriticalSection(&g_cs_send);
        return 1;
    }

    /* Create the IPC window titled "Kegs_<PID>" */
    sprintf(window_title, "Kegs_%lu", (unsigned long)GetCurrentProcessId());
    g_hwnd_shim = CreateWindowEx(
        0,
        KEGS_IPC_WINDOW_CLASS,
        window_title,
        WS_OVERLAPPEDWINDOW,
        CW_USEDEFAULT, CW_USEDEFAULT,
        300, 150,
        NULL, NULL, hInstance, NULL
    );

    if (g_hwnd_shim == NULL) {
        printf("[shim] CreateWindowEx failed: %lu\n",
               (unsigned long)GetLastError());
        MessageBox(NULL, "CreateWindowEx failed", "JaceIPCShim",
                   MB_OK | MB_ICONERROR);
        closesocket(g_tcp_sock);
        WSACleanup();
        DeleteCriticalSection(&g_cs_send);
        return 1;
    }

    /*
     * Show the window so Cyrene's EnumWindows (Attach dialog) can find it.
     * EnumApplicationWindowsProc filters on IsWindowVisible, so SW_SHOW
     * is required for the shim to appear in Cyrene's attach list.
     */
    ShowWindow(g_hwnd_shim, SW_SHOW);
    UpdateWindow(g_hwnd_shim);

    printf("[shim] Window '%s' (class '%s') created and visible\n",
           window_title, KEGS_IPC_WINDOW_CLASS);

    /*
     * Poll for Cyrene's IPC window (class "CyreneIPC").
     * Cyrene may not have opened its IPC window yet; wait up to 10 seconds.
     * This is informational only — the receive thread will also try at runtime.
     */
    printf("[shim] Waiting for Cyrene IPC window (class '%s')...\n",
           CYRENE_IPC_WINDOW_CLASS);
    found = 0;
    for (i = 0; i < CYRENE_FIND_TIMEOUT_MS / CYRENE_FIND_POLL_MS; i++) {
        cyrene_hwnd = find_cyrene_window();
        if (cyrene_hwnd != NULL) {
            g_hwnd_cyrene = cyrene_hwnd;
            printf("[shim] Found Cyrene IPC window: HWND=%p\n",
                   (void *)cyrene_hwnd);
            found = 1;
            break;
        }
        Sleep(CYRENE_FIND_POLL_MS);
    }

    if (!found) {
        printf("[shim] Cyrene IPC window not found within %d ms.\n"
               "       Continuing — will retry when traffic arrives.\n",
               CYRENE_FIND_TIMEOUT_MS);
    }

    /* Start receive thread: reads TCP frames from Jace, sends to Cyrene */
    recv_thread = CreateThread(NULL, 0, ReceiveThread, NULL, 0, &thread_id);
    if (recv_thread == NULL) {
        printf("[shim] CreateThread failed: %lu\n",
               (unsigned long)GetLastError());
        MessageBox(NULL, "CreateThread failed", "JaceIPCShim",
                   MB_OK | MB_ICONERROR);
        DestroyWindow(g_hwnd_shim);
        closesocket(g_tcp_sock);
        WSACleanup();
        DeleteCriticalSection(&g_cs_send);
        return 1;
    }

    printf("[shim] Receive thread started (ID=%lu)\n",
           (unsigned long)thread_id);
    printf("[shim] Ready — attach Cyrene to process '%s'\n", window_title);

    /* Win32 message loop — dispatches WM_COPYDATA from Cyrene to WndProc */
    memset(&msg, 0, sizeof(msg));
    while (GetMessage(&msg, NULL, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }

    printf("[shim] Message loop exited — shutting down\n");

    /* Cleanup */
    closesocket(g_tcp_sock);
    g_tcp_sock = INVALID_SOCKET;

    if (g_pSharedMem) {
        UnmapViewOfFile(g_pSharedMem);
        g_pSharedMem = NULL;
    }
    if (g_hSharedMem) {
        CloseHandle(g_hSharedMem);
        g_hSharedMem = NULL;
    }

    DeleteCriticalSection(&g_cs_send);
    WSACleanup();

    printf("[shim] Exit\n");
    return (int)msg.wParam;
}
