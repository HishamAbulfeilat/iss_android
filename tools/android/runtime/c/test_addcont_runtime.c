#include "../../../../port/src/runtime_infamous.c"

static char mounted_guest[16];
static char mounted_host[512];
static char unmounted_guest[16];

uintptr_t runtime_lookup(const RuntimeExport *table, size_t count, const char *nid) {
    (void)table;
    (void)count;
    (void)nid;
    return 0;
}

int runtime_file_mount(const char *guest, const char *host) {
    snprintf(mounted_guest, sizeof(mounted_guest), "%s", guest);
    snprintf(mounted_host, sizeof(mounted_host), "%s", host);
    return 0;
}

void runtime_file_unmount(const char *guest) {
    snprintf(unmounted_guest, sizeof(unmounted_guest), "%s", guest);
}

int main(int argc, char **argv) {
    if (argc != 2) return 1;
    runtime_infamous_configure(argv[1]);
    uint32_t hits = 0;
    if (addcont_info_list(0, NULL, 0, &hits) != APP_CONTENT_NOT_INITIALIZED) return 2;
    if (app_content_init(NULL, NULL) != 0) return 3;
    if (addcont_info_list(0, NULL, 0, &hits) != 0 || hits != 1) return 4;

    AddcontInfo info[1];
    if (addcont_info_list(0, info, 1, &hits) != 0 || hits != 1 ||
        info[0].status != 4 || strcmp(info[0].entitlement_label.data, "ISS_DLC")) return 5;

    EntitlementLabel entitlement = {{0}, {0}};
    memcpy(entitlement.data, "ISS_DLC", sizeof("ISS_DLC"));
    if (addcont_info(0, &entitlement, &info[0]) != 0 || info[0].status != 4) return 6;
    EntitlementLabel unknown = {{0}, {0}};
    memcpy(unknown.data, "MISSING", sizeof("MISSING"));
    if (addcont_info(0, &unknown, &info[0]) != APP_CONTENT_NO_ENTITLEMENT) return 7;

    AddcontMountPoint mount = {{0}};
    if (addcont_mount(0, &entitlement, &mount) != 0 ||
        strcmp(mount.data, "/addcont0") || strcmp(mounted_guest, "/addcont0")) return 8;
    char expected[512];
    if (snprintf(expected, sizeof(expected), "%s/addcont/CUSA00223/UP9000-CUSA00223_00-ISS_DLC",
                 argv[1]) >= (int)sizeof(expected) || strcmp(mounted_host, expected)) return 9;
    if (addcont_unmount(&mount) != 0 || strcmp(unmounted_guest, "/addcont0") || mount.data[0]) return 10;
    if (addcont_info_list(1, info, 1, &hits) != 0 || hits != 0) return 11;
    return 0;
}
