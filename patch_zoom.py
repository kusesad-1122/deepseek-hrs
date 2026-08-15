path = 'app/src/main/java/com/deepseek/harness/MainActivity.kt'
src = open(path).read()

old = """            cacheMode = WebSettings.LOAD_DEFAULT
            textZoom = 85
        }
"""
new = """            cacheMode = WebSettings.LOAD_DEFAULT
            textZoom = 100
            userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        }
"""
assert old in src, 'settings block not found'
src = src.replace(old, new, 1)

old2 = """            override fun onPageFinished(view: WebView?, url: String?) {
                val js = "document.documentElement.style.zoom=0.8;document.body.style.zoom=0.8;"
                view?.evaluateJavascript(js, null)
                view?.postDelayed({ view.evaluateJavascript(js, null) }, 2000)
                view?.postDelayed({ view.evaluateJavascript(js, null) }, 6000)
            }
"""
new2 = """            override fun onPageFinished(view: WebView?, url: String?) {
                val js = "var m=document.querySelector('meta[name=viewport]');" +
                    "if(m)m.setAttribute('content','width=1280, initial-scale=1.0');" +
                    "document.documentElement.style.zoom=1;document.body.style.zoom=1;"
                view?.evaluateJavascript(js, null)
                view?.postDelayed({ view.evaluateJavascript(js, null) }, 2000)
            }
"""
assert old2 in src, 'onPageFinished not found'
src = src.replace(old2, new2, 1)
open(path, 'w').write(src)
print('desktop mode patched')
