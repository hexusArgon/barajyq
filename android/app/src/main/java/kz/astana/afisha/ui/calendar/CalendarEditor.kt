package kz.astana.afisha.ui.calendar

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import kz.astana.afisha.R
import kz.astana.afisha.domain.AppLanguage
import kz.astana.afisha.domain.EventCard

/** Opens a USER-SAVED draft, never writes to CalendarProvider or claims a save succeeded. */
internal fun calendarEditorIntent(draft: CalendarDraft, reviewNote: String): Intent =
    Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, draft.title)
        putExtra(CalendarContract.Events.EVENT_LOCATION, draft.location)
        putExtra(CalendarContract.Events.DESCRIPTION, listOf(reviewNote, draft.description).joinToString("\n\n"))
        draft.beginMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
        // Model has no structured end time: do NOT invent a duration/end or force all-day.
    }

fun openCalendarEditor(context: Context, card: EventCard, language: AppLanguage?) {
    val intent = calendarEditorIntent(CalendarDraft.from(card, language), context.getString(R.string.calendar_review))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.calendar_unavailable, Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, R.string.calendar_unavailable, Toast.LENGTH_LONG).show()
    }
}
