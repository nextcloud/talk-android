/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import autodagger.AutoInjector
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import io.noties.markwon.Markwon
import java.io.File

@AutoInjector(NextcloudTalkApplication::class)
class FullScreenTextViewerActivity : AppCompatActivity() {

    private lateinit var path: String
    private lateinit var textView: TextView

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_preview, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.share) {
            val shareUri = FileProvider.getUriForFile(
                this,
                BuildConfig.APPLICATION_ID,
                File(path)
            )

            val shareIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, shareUri)
                type = "text/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, resources.getText(R.string.send_to)))

            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_full_screen_text)
        setSupportActionBar(findViewById(R.id.textview_toolbar))
        supportActionBar?.setDisplayShowTitleEnabled(false)
        textView = findViewById(R.id.text_view)

        val fileName = intent.getStringExtra("FILE_NAME")
        val isMarkdown = intent.getBooleanExtra("IS_MARKDOWN", false)
        path = applicationContext.cacheDir.absolutePath + "/" + fileName
        var text = readFile(path)

        if (isMarkdown) {
            val markwon = Markwon.create(applicationContext)
            markwon.setMarkdown(textView, text)
        } else {
            textView.text = text
        }
    }

    private fun readFile(fileName: String) = File(fileName).inputStream().readBytes().toString(Charsets.UTF_8)
}
