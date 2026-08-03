#!/usr/bin/env python3

import os
from pathlib import Path
from flask import (
    Flask,
    request,
    render_template_string,
    send_from_directory,
    redirect,
    url_for,
    abort,
)
from werkzeug.utils import secure_filename

# ==========================
# Configuration
# ==========================

SHARED_FOLDER = os.path.expanduser("~/storage")
PORT = 8080
HOST = "0.0.0.0"

os.makedirs(SHARED_FOLDER, exist_ok=True)

app = Flask(__name__)


# ==========================
# Helpers
# ==========================

def safe_path(rel_path: str):
    """Return absolute path inside shared folder."""
    rel_path = rel_path.strip("/")
    abs_path = os.path.abspath(os.path.join(SHARED_FOLDER, rel_path))

    if not abs_path.startswith(SHARED_FOLDER):
        abort(403)

    return abs_path


def human_size(size):
    units = ["B", "KB", "MB", "GB", "TB"]

    for unit in units:
        if size < 1024:
            return f"{size:.1f} {unit}"
        size /= 1024

    return f"{size:.1f} PB"


# ==========================
# Home
# ==========================

@app.route("/")
@app.route("/browse/")
@app.route("/browse/<path:path>")
def browse(path=""):

    folder = safe_path(path)

    if not os.path.isdir(folder):
        abort(404)

    items = []

    for name in sorted(os.listdir(folder), key=str.lower):
        full = os.path.join(folder, name)

        rel = os.path.relpath(full, SHARED_FOLDER).replace("\\", "/")

        items.append({
            "name": name,
            "rel": rel,
            "is_dir": os.path.isdir(full),
            "size": "" if os.path.isdir(full) else human_size(os.path.getsize(full)),
        })

    parent = ""

    if path:
        parent = os.path.dirname(path)

    return render_template_string("""
<!doctype html>

<html>

<head>
<title>Web Server</title>
<meta name="viewport" content="width=device-width, initial-scale=1">

<style>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap');

*{
    box-sizing:border-box;
    margin:0;
    padding:0;
}

body{
    font-family:'Inter',sans-serif;
    max-width:1000px;
    min-height:100vh;
    margin:0 auto;
    padding:24px;
    background:linear-gradient(135deg,#eef2ff,#f8fafc);
    color:#1e293b;
}

h2{
    margin-bottom:18px;
    color:#0f172a;
}

p{
    margin-bottom:15px;
}

a{
    color:#2563eb;
    text-decoration:none;
    font-weight:500;
}

a:hover{
    text-decoration:underline;
}

.table-container{
    margin-top:15px;
    background:white;
    border-radius:12px;
    box-shadow:0 8px 25px rgba(0,0,0,.08);
    overflow-y:auto;
    overflow-x:hidden;
    max-height:55vh;
    border:1px solid #dbe4ff;
}

table{
    width:100%;
    border-collapse:collapse;
    border-spacing:0;
}

th{
    background:linear-gradient(90deg,#2563eb,#4f46e5);
    color:white;
    text-align:left;
    padding:14px;
    font-weight:600;
}

thead th{
    position:sticky;
    top:0;
    z-index:100;
}

tbody tr:hover{
    background:#f8fafc;
}

td{
    padding:13px 14px;
    border-bottom:1px solid #e5e7eb;
}

tr:last-child td{
    border-bottom:none;
}

tr:hover{
    background:#f8fafc;
}

form{
    margin-top:24px;
    padding:18px;
    background:white;
    border:1px solid #dbe4ff;
    border-radius:12px;
    box-shadow:0 4px 15px rgba(0,0,0,.06);
}

h3{
    margin-bottom:12px;
    color:#334155;
}

input[type=file],
input[type=text],
input[name=dirname]{
    width:100%;
    padding:10px 12px;
    margin:10px 0 16px 0;
    border:1px solid #cbd5e1;
    border-radius:8px;
    font-size:15px;
    background:white;
}

input:focus{
    outline:none;
    border-color:#3b82f6;
    box-shadow:0 0 0 3px rgba(59,130,246,.18);
}

button{
    background:linear-gradient(135deg,#2563eb,#4f46e5);
    color:white;
    border:none;
    padding:10px 22px;
    border-radius:8px;
    cursor:pointer;
    font-size:15px;
    font-weight:600;
    transition:.2s;
    box-shadow:0 4px 12px rgba(37,99,235,.3);
}

button:hover{
    transform:translateY(-2px);
    box-shadow:0 8px 18px rgba(37,99,235,.4);
}

button:active{
    transform:translateY(0);
}

@media(max-width:700px){

    body{
        margin:15px;
        padding:10px;
    }

    table{
        font-size:14px;
    }

    th,td{
        padding:10px;
    }

    button{
        width:100%;
    }
}
</style>

</head>

<body>

<h2>Shared Folder</h2>

<p><b>Current:</b> /{{path}}</p>

{% if path %}
<p><a href="{{url_for('browse', path=parent)}}">⬅ Parent</a></p>
{% endif %}

<div class="table-container">

<table>

<thead>
<tr>
<th>Name</th>
<th>Type</th>
<th>Size</th>
</tr>
</thead>

<tbody>

{% for item in items %}

<tr>

<td>

{% if item.is_dir %}
📁 <a href="{{url_for('browse', path=item.rel)}}">{{item.name}}</a>
{% else %}
📄 <a href="{{url_for('download', path=item.rel)}}">{{item.name}}</a>
{% endif %}

</td>

<td>
{{"Folder" if item.is_dir else "File"}}
</td>

<td>
{{item.size}}
</td>

</tr>

{% endfor %}

</tbody>

</table>

</div>

<form action="/upload/{{path}}" method="post" enctype="multipart/form-data">

<h3>Upload Files</h3>

<input type="file" name="files" multiple>

<button>Upload</button>

</form>

<form action="/mkdir/{{path}}" method="post">

<h3>Create Folder</h3>

<input name="dirname" placeholder="Folder Name">

<button>Create</button>

</form>

</body>

</html>
""",
items=items,
path=path,
parent=parent,
)


# ==========================
# Download
# ==========================

@app.route("/download/<path:path>")
def download(path):

    full = safe_path(path)

    if not os.path.isfile(full):
        abort(404)

    directory = os.path.dirname(full)
    filename = os.path.basename(full)

    return send_from_directory(
        directory,
        filename,
        as_attachment=True,
    )


# ==========================
# Upload
# ==========================

@app.route("/upload/", defaults={"path": ""}, methods=["POST"])
@app.route("/upload/<path:path>", methods=["POST"])
def upload(path):

    folder = safe_path(path)

    if not os.path.isdir(folder):
        abort(404)

    files = request.files.getlist("files")

    for file in files:

        if file.filename == "":
            continue

        filename = secure_filename(file.filename)

        dest = os.path.join(folder, filename)

        # Rename duplicates automatically
        if os.path.exists(dest):

            stem = Path(filename).stem
            suffix = Path(filename).suffix

            i = 1

            while True:

                new_name = f"{stem}_{i}{suffix}"
                dest = os.path.join(folder, new_name)

                if not os.path.exists(dest):
                    break

                i += 1

        file.save(dest)

    return redirect(url_for("browse", path=path))


# ==========================
# Create Folder
# ==========================

@app.route("/mkdir/", defaults={"path": ""}, methods=["POST"])
@app.route("/mkdir/<path:path>", methods=["POST"])
def mkdir(path):

    folder = safe_path(path)

    dirname = request.form.get("dirname", "").strip()

    if dirname:

        dirname = secure_filename(dirname)

        os.makedirs(
            os.path.join(folder, dirname),
            exist_ok=True,
        )

    return redirect(url_for("browse", path=path))


# ==========================
# Main
# ==========================

if __name__ == "__main__":

    print("=" * 40)
    print(" Simple Flask File Server")
    print("=" * 40)
    print(f"Shared folder : {SHARED_FOLDER}")
    print(f"Listening     : http://0.0.0.0:{PORT}")
    print("=" * 40)

    app.run(
        host=HOST,
        port=PORT,
        debug=False,
    )
