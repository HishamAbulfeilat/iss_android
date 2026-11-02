/* System functions only inFAMOUS Second Son (CUSA00223 1.07) imports. */
#include <dirent.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>

#include "runtime.h"

#define APP_CONTENT_NOT_INITIALIZED ((int32_t)0x80d90001)
#define APP_CONTENT_INVALID_PARAMETER ((int32_t)0x80d90002)
#define APP_CONTENT_NOT_FOUND ((int32_t)0x80d90005)
#define APP_CONTENT_NO_ENTITLEMENT ((int32_t)0x80d90007)
#define NP_SIGNED_OUT ((int32_t)0x80550006)
#define SYSTEM_SERVICE_INVALID ((int32_t)0x80a10003)
#define ADDCONT_MAX 16

typedef struct {
    char data[17];
    char padding[3];
} EntitlementLabel;

typedef struct {
    EntitlementLabel entitlement_label;
    uint32_t status;
} AddcontInfo;

typedef struct {
    char data[16];
} AddcontMountPoint;

typedef struct {
    char label[17];
    char directory[512];
} InstalledAddcont;

static InstalledAddcont installed[ADDCONT_MAX];
static const char *const addcont_mount_names[ADDCONT_MAX] = {
    "/addcont0", "/addcont1", "/addcont2", "/addcont3", "/addcont4", "/addcont5",
    "/addcont6", "/addcont7", "/addcont8", "/addcont9", "/addcont10", "/addcont11",
    "/addcont12", "/addcont13", "/addcont14", "/addcont15"};
static unsigned installed_count;
static int app_content_initialized;

static void once(const char *what) { printf("Runtime: %s (inFAMOUS)\n", what); }

/* sceSystemServiceParamGetString(id, buffer, size): only the user name parameter exists. */
static ABI int32_t param_string(int32_t id, char *buffer, uint64_t size) {
    if (!buffer || !size) return SYSTEM_SERVICE_INVALID;
    snprintf(buffer, size, "%s", "Player");
    printf("Runtime: sceSystemServiceParamGetString(%d) -> Player\n", id);
    return 0;
}

static ABI int32_t ok(void) { return 0; }
static ABI int32_t signed_out(void) { return NP_SIGNED_OUT; }
static ABI int32_t np_register_a(void *callback, void *arg) {
    (void)callback;
    (void)arg;
    static int32_t next = 0x100;
    return ++next;
}
static ABI int32_t netctl_init(void) { once("sceNetCtlInit"); return 0; }

static ABI int32_t app_content_init(void *init_param, void *boot_param) {
    (void)init_param;
    (void)boot_param;
    app_content_initialized = 1;
    return 0;
}

static ABI int32_t addcont_info(uint32_t service_label, const EntitlementLabel *entitlement,
                                AddcontInfo *info) {
    if (!app_content_initialized) return APP_CONTENT_NOT_INITIALIZED;
    if (service_label > 7 || !entitlement || !info) return APP_CONTENT_INVALID_PARAMETER;
    for (unsigned i = 0; i < installed_count; ++i) {
        if (service_label == 0 && !strncmp(entitlement->data, installed[i].label, sizeof(entitlement->data))) {
            memset(info, 0, sizeof(*info));
            memcpy(info->entitlement_label.data, installed[i].label, sizeof(info->entitlement_label.data));
            info->status = 4; /* installed */
            return 0;
        }
    }
    return APP_CONTENT_NO_ENTITLEMENT;
}

static ABI int32_t addcont_info_list(uint32_t service_label, AddcontInfo *list, uint32_t list_num,
                                     uint32_t *hit_num) {
    if (!app_content_initialized) return APP_CONTENT_NOT_INITIALIZED;
    if (service_label > 7 || (list_num && !list) || ((!list_num || !list) && !hit_num))
        return APP_CONTENT_INVALID_PARAMETER;
    const uint32_t count = service_label == 0 ? installed_count : 0;
    if (!list_num || !list) {
        *hit_num = count;
        return 0;
    }
    const uint32_t written = count < list_num ? count : list_num;
    for (uint32_t i = 0; i < written; ++i) {
        memset(&list[i], 0, sizeof(list[i]));
        memcpy(list[i].entitlement_label.data, installed[i].label,
               sizeof(list[i].entitlement_label.data));
        list[i].status = 4;
    }
    if (hit_num) *hit_num = written;
    return 0;
}

static ABI int32_t addcont_mount(uint32_t service_label, const EntitlementLabel *entitlement,
                                 AddcontMountPoint *mount_point) {
    if (!app_content_initialized) return APP_CONTENT_NOT_INITIALIZED;
    if (service_label > 7 || !entitlement || !mount_point) return APP_CONTENT_INVALID_PARAMETER;
    for (unsigned i = 0; i < installed_count; ++i) {
        if (service_label != 0 || strncmp(entitlement->data, installed[i].label,
                                          sizeof(entitlement->data))) continue;
        const char *guest = addcont_mount_names[i];
        if (runtime_file_mount(guest, installed[i].directory)) return APP_CONTENT_NOT_FOUND;
        memset(mount_point, 0, sizeof(*mount_point));
        memcpy(mount_point->data, guest, strlen(guest) + 1);
        return 0;
    }
    return APP_CONTENT_NOT_FOUND;
}

static ABI int32_t addcont_unmount(AddcontMountPoint *mount_point) {
    if (!app_content_initialized) return APP_CONTENT_NOT_INITIALIZED;
    if (!mount_point || !memchr(mount_point->data, '\0', sizeof(mount_point->data)) ||
        strncmp(mount_point->data, "/addcont", 8)) return APP_CONTENT_INVALID_PARAMETER;
    char guest[sizeof(mount_point->data)];
    memcpy(guest, mount_point->data, sizeof(guest));
    runtime_file_unmount(guest);
    memset(mount_point, 0, sizeof(*mount_point));
    return 0;
}

static int valid_content_id(const char *content_id, char *label, size_t label_size) {
    const size_t length = strlen(content_id);
    if (length <= 20 || length > 255 || content_id[6] != '-' ||
        strncmp(content_id + 7, "CUSA00223", 9) ||
        content_id[16] != '_' || content_id[19] != '-') return 0;
    for (unsigned i = 0; i < 6; ++i) {
        if (i < 2 ? content_id[i] < 'A' || content_id[i] > 'Z'
                  : content_id[i] < '0' || content_id[i] > '9') return 0;
    }
    if (content_id[17] < '0' || content_id[17] > '9' ||
        content_id[18] < '0' || content_id[18] > '9') return 0;
    const char *entitlement = content_id + 20;
    const size_t entitlement_length = length - 20;
    if (!entitlement_length || entitlement_length >= label_size) return 0;
    for (size_t i = 0; i < entitlement_length; ++i) {
        const char c = entitlement[i];
        if (!((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-')) return 0;
    }
    memcpy(label, entitlement, entitlement_length);
    label[entitlement_length] = '\0';
    return 1;
}

void runtime_infamous_configure(const char *app0) {
    installed_count = 0;
    app_content_initialized = 0;
    if (!app0 || !*app0) return;
    char root[512];
    if (snprintf(root, sizeof(root), "%s/addcont/CUSA00223", app0) >= (int)sizeof(root)) return;
    DIR *directory = opendir(root);
    if (!directory) return;
    struct dirent *entry;
    while (installed_count < ADDCONT_MAX && (entry = readdir(directory)) != NULL) {
        if (entry->d_name[0] == '.') continue;
        char content_dir[512], marker[600], content_id[256], label[17];
        struct stat st;
        if (snprintf(content_dir, sizeof(content_dir), "%s/%s", root, entry->d_name) >= (int)sizeof(content_dir) ||
            lstat(content_dir, &st) || !S_ISDIR(st.st_mode)) continue;
        if (snprintf(marker, sizeof(marker), "%s/.iss-content-id", content_dir) >= (int)sizeof(marker)) continue;
        if (lstat(marker, &st) || !S_ISREG(st.st_mode)) continue;
        FILE *file = fopen(marker, "r");
        if (!file) continue;
        const int read = fgets(content_id, sizeof(content_id), file) != NULL;
        fclose(file);
        if (!read) continue;
        content_id[strcspn(content_id, "\r\n")] = '\0';
        if (!valid_content_id(content_id, label, sizeof(label)) ||
            strcmp(entry->d_name, content_id)) continue;
        int duplicate = 0;
        for (unsigned i = 0; i < installed_count; ++i)
            if (!strcmp(installed[i].label, label)) duplicate = 1;
        if (duplicate) continue;
        snprintf(installed[installed_count].label, sizeof(installed[installed_count].label), "%s", label);
        snprintf(installed[installed_count].directory, sizeof(installed[installed_count].directory), "%s",
                 content_dir);
        ++installed_count;
    }
    closedir(directory);
    if (installed_count) printf("Runtime: discovered %u installed inFAMOUS add-on(s)\n", installed_count);
}

/* scePlayGoGetEta(handle, chunks, count, out_eta): everything is installed. */
static ABI int32_t playgo_eta(int32_t handle, const uint16_t *chunks, uint32_t count, uint64_t *eta) {
    (void)handle; (void)chunks; (void)count;
    if (eta) *eta = 0;
    return 0;
}

/* scePlayGoGetToDoList(handle, list, count, out_entries): no pending chunk. */
static ABI int32_t playgo_todo(int32_t handle, void *list, uint32_t count, uint32_t *entries) {
    (void)handle; (void)list; (void)count;
    if (entries) *entries = 0;
    return 0;
}

static ABI int32_t playgo_language_mask(int32_t handle, uint64_t mask) {
    printf("Runtime: scePlayGoSetLanguageMask(%#llx)\n", (unsigned long long)mask);
    (void)handle;
    return 0;
}

static const RuntimeExport exports[] = {
    {"sceSystemServiceParamGetString", param_string},
    {"sceHttpSetChunkedTransferEnabled", ok}, {"sceHttpsLoadCert", ok},
    {"sceNetCtlInit", netctl_init}, {"sceNetCtlTerm", ok},
    {"scePlayGoGetEta", playgo_eta}, {"scePlayGoGetToDoList", playgo_todo},
    {"scePlayGoSetLanguageMask", playgo_language_mask},
    {"sceNpCheckNpAvailabilityA", signed_out}, {"sceNpGetAccountCountryA", signed_out},
    {"sceNpGetAccountIdA", signed_out}, {"sceNpGetGamePresenceStatusA", signed_out},
    {"sceNpRegisterGamePresenceCallbackA", np_register_a}, {"sceNpRegisterStateCallbackA", np_register_a},
    {"sceAppContentInitialize", app_content_init},
    {"sceAppContentGetAddcontInfo", addcont_info},
    {"sceAppContentGetAddcontInfoList", addcont_info_list},
    {"sceAppContentAddcontMount", addcont_mount},
    {"sceAppContentAddcontUnmount", addcont_unmount},
};

uintptr_t runtime_infamous_resolve(const char *name) {
    return RUNTIME_LOOKUP(exports, name);
}
