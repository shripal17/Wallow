package com.codertainment.wallow.activity

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.StringRequestListener
import com.codertainment.wallow.getBoxStore
import com.codertainment.wallow.getCategoryBox
import com.codertainment.wallow.getWallpaperBox
import com.codertainment.wallow.model.Category
import com.codertainment.wallow.model.Wallpaper
import com.codertainment.wallow.util.ApiService
import com.codertainment.wallow.util.UIUtils
import com.mcxiaoke.koi.ext.isConnected
import com.mcxiaoke.koi.ext.startActivity
import com.mcxiaoke.koi.log.logd
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

class SplashActivity : AppCompatActivity() {

  var categories = ArrayList<Category>()
  var wallpapers = ArrayList<Wallpaper>()
  var categoryCounter = 0
  var disposable: Disposable? = null
  var featuredWalls = ArrayList<Pair<String, String?>>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    UIUtils.setBarTranslucent(this, true, true)

    if (isConnected()) {
      logd("Loading", "Started")
      disposable = ApiService.getInstance().getDirectoryContents().subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
        .subscribe(
          {
            it.filter { it.type == "dir" }.forEach { resp ->
              if (!resp.isWallpaper()) {
                val category = Category(name = resp.name, icon = it.find { it.name == resp.name + ".png" }?.downloadUrl)
                categories.add(category)
              }
            }
            getBoxStore().runInTx {
              getCategoryBox().removeAll()
              getCategoryBox().put(categories)
            }
            loadFeatured()
          },
          {
            it.printStackTrace()
          }
        )
    } else if (getCategoryBox().count() == 0L || getWallpaperBox().count() == 0L) {
      AlertDialog.Builder(this)
        .setTitle("Offline")
        .setMessage("An active internet connection is required for the first-time setup")
        .setPositiveButton("Ok") { dialogInterface, _ ->
          dialogInterface.dismiss()
          finish()
        }
        .create().show()
    } else {
      openHome()
    }
  }

  private fun loadFeatured() {
    AndroidNetworking.get("https://raw.githubusercontent.com/${ApiService.REPO_OWNER}/${ApiService.REPO_NAME}/master/featured.txt")
      .build()
      .getAsString(object : StringRequestListener {
        override fun onResponse(response: String?) {
          val featured = response?.split(",")
          val featuredCategories = featured?.map { it.split("/")[0] }
          val featuredNames = featured?.map { it.split("/")[1] }
          featuredCategories?.forEachIndexed { i, it ->
            val p = Pair(it, featuredNames?.get(i))
            if (p.second != null) {
              featuredWalls.add(p)
            }
          }
          logd("response", response.toString())
          logd("cats", featuredCategories.toString())
          logd("names", featuredNames.toString())
          logd("f walls", featuredWalls.toString())
          categories.forEach {
            it.load()
          }
        }

        override fun onError(anError: ANError?) {

        }

      })
  }

  private fun loadNextCategory() {
    if (categoryCounter < categories.size) {
      loadDir()
      categoryCounter++
    } else {
      getBoxStore().runInTx {
        getWallpaperBox().removeAll()
        getWallpaperBox().put(wallpapers)
      }
      openHome()
    }
  }

  private fun openHome() {
    logd("Loading", "Completed")
    getBoxStore().runInTx {
      getWallpaperBox().removeAll()
      getWallpaperBox().put(wallpapers)
    }
    startActivity<MainActivity>()
    finish()
  }

  private fun loadDir() {
    val currCategory = categories[categoryCounter]
    disposable = ApiService.getInstance().getDirectoryContents(currCategory.name).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
      .subscribe(
        {
          it.filter { it.type == "file" }.forEach { resp ->
            if (resp.isWallpaper()) {
              val wallpaper =
                Wallpaper(name = resp.name, size = resp.size, categoryId = currCategory.id, categoryName = currCategory.name, link = resp.downloadUrl)
              wallpapers.add(wallpaper)
            }
          }
          loadNextCategory()
        },
        {
          it.printStackTrace()
        }
      )
  }

  private fun Category.load() {
    val currCategory = this
    disposable = ApiService.getInstance().getDirectoryContents(currCategory.name).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
      .subscribe(
        {
          it.filter { it.type == "file" }.forEach { resp ->
            if (resp.isWallpaper()) {
              val wallpaper =
                Wallpaper(name = resp.name, size = resp.size, categoryId = currCategory.id, categoryName = currCategory.name, link = resp.downloadUrl)
              val currPair = Pair(wallpaper.categoryName, wallpaper.name)
              logd("currPair", currPair.toString())
              wallpaper.featured = featuredWalls.contains(currPair)
              wallpapers.add(wallpaper)
            }
          }
          categoryCounter++
          if (categoryCounter == categories.size) {
            openHome()
          }
        },
        {
          it.printStackTrace()
        }
      )
  }

  override fun onDestroy() {
    disposable?.dispose()
    super.onDestroy()
  }
}
