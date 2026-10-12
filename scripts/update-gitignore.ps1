$content = Get-Content .gitignore
if ($content -notmatch 'logs/android/'){
    Add-Content .gitignore 'logs/android/'
}
