/* inFAMOUS Second Son: read-only menu state, verified against the player's ELF.
 * CMenu::Activate (0x27db10) increments this count once after setting +0x61b;
 * CMenu::Deactivate (0x27e210) clears that flag and decrements it, clamped at 0.
 * Submenu replacement transfers the active flag without changing the count.
 * This is the menu lifecycle, not a heuristic based on time scale or Start. */
#pragma once
#include <stddef.h>
#include <stdint.h>
#include <string.h>

#define ISS_MENU_COUNT_OFFSET UINT64_C(0x1d1f7fc)
#define ISS_MENU_OPEN_SITE UINT64_C(0x27db27)
#define ISS_MENU_CLOSE_SITE UINT64_C(0x27e226)
static const uint8_t iss_menu_open_signature[]={
    0x4c,0x8d,0x35,0xce,0x1c,0xaa,0x01, /* lea r14,[image+0x1d1f7fc] */
    0xc6,0x83,0x1b,0x06,0x00,0x00,0x01, /* active flag = 1 */
    0x41,0x8b,0x06,0x85,0xc0
};
static const uint8_t iss_menu_close_signature[]={
    0x48,0x8d,0x05,0xcf,0x15,0xaa,0x01, /* lea rax,[same count] */
    0xc6,0x83,0x1b,0x06,0x00,0x00,0x00, /* active flag = 0 */
    0x31,0xd2,0x8b,0x08,0xff,0xc9,0x0f,0x48,0xca,0x89,0x08
};
static inline int iss_menu_signatures_match(const uint8_t *image,uint64_t size){
    return image && size>=ISS_MENU_COUNT_OFFSET+4 &&
        !memcmp(image+ISS_MENU_OPEN_SITE,iss_menu_open_signature,sizeof(iss_menu_open_signature)) &&
        !memcmp(image+ISS_MENU_CLOSE_SITE,iss_menu_close_signature,sizeof(iss_menu_close_signature));
}
/* -1: unsupported/invalid; 0: gameplay; 1: at least one active menu. */
static inline int iss_menu_read(const uint32_t *counter){
    if(!counter)return -1;
    const uint32_t n=__atomic_load_n(counter,__ATOMIC_RELAXED);
    return n<=64 ? n!=0 : -1;
}
