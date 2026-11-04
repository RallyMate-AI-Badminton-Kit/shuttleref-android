/* Offline Linux migration harness. No cloud connection or durable session yet. */
#include "match_session.h"
#include "phone_command.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static void apply(match_session_t *session, se_state_t *score,
                  phone_command_state_t *history, const char *wire)
{
    char command[65];
    if (!match_command_decode(wire, command, sizeof(command))) return;
    if (match_session_apply(session, score, command)) return;
    if (session->state != 'a' || !session->id[0]) return;
    const char *sequence = NULL;
    if (!strncmp(command, "point:red:", 10)) sequence = command + 10;
    else if (!strncmp(command, "point:blue:", 11)) sequence = command + 11;
    if (!sequence || strncmp(sequence, session->id, 32) || sequence[32] != '_') return;
    uint32_t number = (uint32_t)strtoul(sequence + 33, NULL, 16);
    if (number <= session->point_watermark) return;
    session->watermark = session->point_watermark = number;
    snprintf(session->token, sizeof(session->token), "%08lx", (unsigned long)number);
    session->code = 0;
    (void)phone_command_apply(score, history, command, strlen(command));
}

static void report(const match_session_t *session, const se_state_t *score)
{
    char snapshot[65];
    match_session_report(session, score, snapshot, sizeof(snapshot));
    const char *phase = session->state == 'p' ? "replay_paused" :
        session->state == 'a' ? (score->match_over ? "finished" : "refereeing") : "standby";
    printf("{\"101\":\"%s\",\"102\":\"%s\",\"108\":%u,\"109\":%u}\n",
           snapshot, phase, score->score_red, score->score_blue);
    fflush(stdout);
}

int main(void)
{
    se_state_t score;
    match_session_t session = {0};
    phone_command_state_t history = {0};
    se_init(&score);
    fprintf(stderr, "ShuttleRef OFFLINE harness: one DP110 base64 command per line.\n"
                    "No cloud connection. State is lost when this process exits.\n");
    char input[128];
    while (fgets(input, sizeof(input), stdin)) {
        size_t length = strlen(input);
        int overflow = length && input[length - 1] != '\n' && !feof(stdin);
        if (overflow) {
            int c;
            while ((c = getchar()) != '\n' && c != EOF) { }
        } else {
            input[strcspn(input, "\r\n")] = 0;
            apply(&session, &score, &history, input);
        }
        report(&session, &score);
    }
    return ferror(stdin) ? 1 : 0;
}
