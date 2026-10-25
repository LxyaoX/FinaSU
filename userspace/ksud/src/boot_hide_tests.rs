use super::{PROPERTIES, apply_with};
use std::collections::HashMap;

#[test]
fn missing_property_is_not_set() {
    let values = HashMap::<String, String>::new();
    let writes = apply_with(
        |key| values.get(key).cloned(),
        |_, _| panic!("missing property was set"),
        |_| panic!("missing delete property was removed"),
    );
    assert_eq!(writes, 0);
}

#[test]
fn matching_property_is_not_set() {
    let (key, value) = PROPERTIES[0];
    let values = HashMap::from([(key.to_owned(), value.to_owned())]);
    let writes = apply_with(
        |key| values.get(key).cloned(),
        |_, _| panic!("matching property was set"),
        |_| panic!("missing delete property was removed"),
    );
    assert_eq!(writes, 0);
}

#[test]
fn changed_properties_are_counted() {
    let first = PROPERTIES[0];
    let second = PROPERTIES[1];
    let values = HashMap::from([
        (first.0.to_owned(), "orange".to_owned()),
        (second.0.to_owned(), "orange".to_owned()),
    ]);
    let mut writes = Vec::new();
    let count = apply_with(
        |key| values.get(key).cloned(),
        |key, value| {
            writes.push((key.to_owned(), value.to_owned()));
            false
        },
        |_| panic!("missing delete property was removed"),
    );
    assert_eq!(count, 2);
    assert_eq!(
        writes,
        vec![
            (first.0.to_owned(), first.1.to_owned()),
            (second.0.to_owned(), second.1.to_owned())
        ]
    );
}

#[test]
fn missing_delete_target_is_not_deleted() {
    let values = HashMap::<String, String>::new();
    let mut deleted = false;
    let writes = apply_with(
        |key| values.get(key).cloned(),
        |_, _| panic!("missing property was set"),
        |_| deleted = true,
    );
    assert_eq!(writes, 0);
    assert!(!deleted);
}
