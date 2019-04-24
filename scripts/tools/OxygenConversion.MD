brew install coreutils
find * -type l -exec bash -c 'ln -f "$(greadlink -m "$0")" "$0"' {} \;
rename 's/-/_/g' *
rename 's/\+/_/g' *
rename 's/\./_/g' *
rename 's/_png/\.png/g' *

